package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.application.TransportRequestCreationResult;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.GeneralSupplementalAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.SupplementalAssessmentRecordRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransportRequestConcurrencyIntegrationTest {

    @Autowired private TransportRequestService transportRequestService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private CurrentPatientSnapshotRepository currentPatientSnapshotRepository;
    @Autowired private SupplementalAssessmentRecordRepository supplementalAssessmentRecordRepository;
    @Autowired private GeneralSupplementalAssessmentRepository generalSupplementalAssessmentRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void simultaneousIdenticalRetriesCreateOnlyOneCompleteRequest() throws Exception {
        Organization organization = organizationRepository.save(Organization.create(
                "동시 요청 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "parallelmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0002"));
        consentRepository.save(ContactSharingConsent.record(
                account,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-03T09:00:00Z")
        ));
        AuthenticatedAccount authenticated = new AuthenticatedAccount(
                account.getPublicId(),
                organization.getPublicId(),
                UserRole.PARAMEDIC
        );
        var base = ValidTransportRequestFixtures.request();
        var request = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                base.patient(),
                base.incident(),
                base.preKtas(),
                base.consciousness(),
                base.vitalSigns(),
                base.treatments(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.SupplementalAssessmentInput(
                        Instant.parse("2026-08-03T10:00:00Z"),
                        Instant.parse("2026-08-03T10:01:00Z"),
                        85,
                        null,
                        null,
                        "고혈압",
                        null,
                        null,
                        false
                )
        );

        List<TransportRequestCreationResult> results = runTogether(
                () -> transportRequestService.create(
                        authenticated,
                        "parallel-request-key",
                        request
                ),
                () -> transportRequestService.create(
                        authenticated,
                        "parallel-request-key",
                        request
                )
        );

        assertThat(results).filteredOn(TransportRequestCreationResult::created).hasSize(1);
        assertThat(results).filteredOn(result -> !result.created()).hasSize(1);
        assertThat(results.get(0).response().transportRequestId())
                .isEqualTo(results.get(1).response().transportRequestId());
        assertThat(transportRequestRepository.count()).isEqualTo(1);
        assertThat(currentPatientSnapshotRepository.count()).isEqualTo(1);
        assertThat(supplementalAssessmentRecordRepository.count()).isEqualTo(1);
        assertThat(generalSupplementalAssessmentRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.TRANSPORT_REQUEST_CREATED)).isEqualTo(1);
    }

    private List<TransportRequestCreationResult> runTogether(
            Callable<TransportRequestCreationResult> first,
            Callable<TransportRequestCreationResult> second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransportRequestCreationResult> firstResult = executor.submit(waitThenRun(ready, start, first));
            Future<TransportRequestCreationResult> secondResult = executor.submit(waitThenRun(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private Callable<TransportRequestCreationResult> waitThenRun(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<TransportRequestCreationResult> delegate
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return delegate.call();
        };
    }
}
