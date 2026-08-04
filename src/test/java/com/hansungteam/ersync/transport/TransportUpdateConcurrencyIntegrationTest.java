package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.ClinicalUpdateResult;
import com.hansungteam.ersync.transport.application.TransportClinicalUpdateService;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransportUpdateConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository profileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private TransportClinicalUpdateService clinicalUpdateService;
    @Autowired private TransportLocationService locationService;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private CurrentPatientSnapshotRepository snapshotRepository;
    @Autowired private TransportCurrentLocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentClinicalAndLocationCommandsPreserveEveryRecordAndNewestState() throws Exception {
        UserAccount paramedic = createParamedic("updateconcurrencymedic");
        AuthenticatedAccount authenticated = new AuthenticatedAccount(
                paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
        String requestId = requestService.create(
                authenticated, "update-concurrency-request", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        Instant initialMeasuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt();

        runTogether(
                () -> clinicalUpdateService.addVitalSigns(
                        authenticated,
                        requestId,
                        "concurrent-clinical-old",
                        vitalRequest(initialMeasuredAt.plusSeconds(30))
                ),
                () -> clinicalUpdateService.addVitalSigns(
                        authenticated,
                        requestId,
                        "concurrent-clinical-new",
                        vitalRequest(initialMeasuredAt.plusSeconds(60))
                )
        );

        var snapshot = snapshotRepository.findByTransportRequestPublicId(requestId).orElseThrow();
        assertThat(snapshot.getLatestVitalSignSet().getMeasuredAt()).isEqualTo(initialMeasuredAt.plusSeconds(60));
        assertThat(countRows("vital_sign_sets", requestId)).isEqualTo(3);

        Instant capturedAt = Instant.parse("2026-08-04T10:00:00Z");
        runTogether(
                () -> locationService.update(
                        authenticated,
                        requestId,
                        "concurrent-location-old",
                        new UpdateTransportLocationRequest(
                                new BigDecimal("37.1000000"), new BigDecimal("127.1000000"), capturedAt
                        )
                ),
                () -> locationService.update(
                        authenticated,
                        requestId,
                        "concurrent-location-new",
                        new UpdateTransportLocationRequest(
                                new BigDecimal("37.2000000"), new BigDecimal("127.2000000"),
                                capturedAt.plusSeconds(10)
                        )
                )
        );

        var request = requestRepository.findByPublicId(requestId).orElseThrow();
        var location = locationRepository.findByTransportRequestId(request.getId()).orElseThrow();
        assertThat(location.getLatitude()).isEqualByComparingTo("37.2000000");
        assertThat(location.getLongitude()).isEqualByComparingTo("127.2000000");
        assertThat(location.getCapturedAt()).isEqualTo(capturedAt.plusSeconds(10));
        assertThat(countRows("transport_current_locations", requestId)).isEqualTo(1);
        assertThat(countRows("transport_update_commands", requestId)).isEqualTo(4);
    }

    @Test
    void simultaneousIdenticalClinicalRetriesCreateOneRecordAndReplayOneResult() throws Exception {
        UserAccount paramedic = createParamedic("updateidenticalretrymedic");
        AuthenticatedAccount authenticated = principal(paramedic);
        String requestId = requestService.create(
                authenticated, "identical-retry-request", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        Instant measuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt().plusSeconds(30);
        UpdateVitalSignsRequest input = vitalRequest(measuredAt);
        AtomicReference<ClinicalUpdateResult> first = new AtomicReference<>();
        AtomicReference<ClinicalUpdateResult> second = new AtomicReference<>();

        runTogether(
                () -> first.set(clinicalUpdateService.addVitalSigns(
                        authenticated, requestId, "simultaneous-identical-retry", input
                )),
                () -> second.set(clinicalUpdateService.addVitalSigns(
                        authenticated, requestId, "simultaneous-identical-retry", input
                ))
        );

        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isNotNull();
        assertThat(List.of(first.get().created(), second.get().created()))
                .containsExactlyInAnyOrder(true, false);
        assertThat(first.get().response().recordId()).isEqualTo(second.get().response().recordId());
        assertThat(List.of(
                first.get().response().idempotentReplay(),
                second.get().response().idempotentReplay()
        )).containsExactlyInAnyOrder(false, true);
        assertThat(countRows("vital_sign_sets", requestId)).isEqualTo(2);
        assertThat(countRows("transport_update_commands", requestId)).isEqualTo(1);
    }

    @Test
    void simultaneousClinicalAndLocationReuseOfOneKeyCommitsOnlyOneCommand() throws Exception {
        UserAccount paramedic = createParamedic("updatecrosscommandmedic");
        AuthenticatedAccount authenticated = principal(paramedic);
        String requestId = requestService.create(
                authenticated, "cross-command-request", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        Instant measuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt().plusSeconds(30);
        Instant capturedAt = Instant.parse("2026-08-04T10:30:00Z");
        AtomicInteger successCount = new AtomicInteger();
        CopyOnWriteArrayList<String> errorCodes = new CopyOnWriteArrayList<>();

        runTogether(
                () -> captureOutcome(successCount, errorCodes, () -> clinicalUpdateService.addVitalSigns(
                        authenticated,
                        requestId,
                        "shared-cross-command-key",
                        vitalRequest(measuredAt)
                )),
                () -> captureOutcome(successCount, errorCodes, () -> locationService.update(
                        authenticated,
                        requestId,
                        "shared-cross-command-key",
                        new UpdateTransportLocationRequest(
                                new BigDecimal("37.3000000"),
                                new BigDecimal("127.3000000"),
                                capturedAt
                        )
                ))
        );

        long addedClinicalRows = countRows("vital_sign_sets", requestId) - 1;
        long locationRows = countRows("transport_current_locations", requestId);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(errorCodes).containsExactly("COMMON_005");
        assertThat(addedClinicalRows + locationRows).isEqualTo(1);
        assertThat(countRows("transport_update_commands", requestId)).isEqualTo(1);
    }

    private void captureOutcome(AtomicInteger successCount, CopyOnWriteArrayList<String> errorCodes, Runnable action) {
        try {
            action.run();
            successCount.incrementAndGet();
        } catch (CustomException exception) {
            errorCodes.add(exception.getErrorCode().getCode());
        }
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstFuture = executor.submit(awaitThenRun(ready, start, first));
            Future<?> secondFuture = executor.submit(awaitThenRun(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFuture.get(10, TimeUnit.SECONDS);
            secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Runnable awaitThenRun(CountDownLatch ready, CountDownLatch start, Runnable command) {
        return () -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent update start signal timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent update interrupted", exception);
            }
            command.run();
        };
    }

    private UpdateVitalSignsRequest vitalRequest(Instant measuredAt) {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        return new UpdateVitalSignsRequest(
                measuredAt,
                measuredAt.plusSeconds(1),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(), measurement.state(), measurement.primaryValue(),
                        measurement.secondaryValue(), measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
    }

    private AuthenticatedAccount principal(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(), account.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
    }

    private long countRows(String table, String requestId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " records "
                        + "JOIN transport_requests requests ON records.transport_request_id = requests.id "
                        + "WHERE requests.public_id = ?",
                Long.class,
                requestId
        );
        return count == null ? 0 : count;
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.PARAMEDIC
        ));
        profileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0001"));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }
}
