package com.hansungteam.ersync.hospital.search;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
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
class HospitalSearchConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private HospitalOfferService hospitalOfferService;

    @Test
    void finalTimeoutAndHospitalAcceptanceCannotProduceMixedState() throws Exception {
        UserAccount paramedic = createParamedic();
        UserAccount hospital = createHospital();
        var creation = transportRequestService.create(
                authenticated(paramedic, UserRole.PARAMEDIC),
                "race-search-request",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        attempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        attempt.scheduleNextExpansion(100, true, Instant.EPOCH);
        attemptRepository.saveAndFlush(attempt);
        var offer = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId()).getFirst();
        Long attemptId = attempt.getId();

        List<Outcome> outcomes = runTogether(
                () -> {
                    hospitalSearchService.processDueAttempt(attemptId);
                    return Outcome.success();
                },
                () -> {
                    try {
                        hospitalOfferService.accept(
                                authenticated(hospital, UserRole.HOSPITAL_STAFF),
                                offer.getPublicId(),
                                "race-accept-key"
                        );
                        return Outcome.success();
                    } catch (CustomException exception) {
                        return Outcome.failure(exception.getErrorCode().getCode());
                    }
                }
        );

        var storedOffer = offerRepository.findById(offer.getId()).orElseThrow();
        var storedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        var storedRequest = transportRequestRepository.findByPublicId(
                creation.response().transportRequestId()
        ).orElseThrow();
        assertThat(storedOffer.getStatus()).isEqualTo(HospitalOfferStatus.ACCEPTED);
        assertThat(storedAttempt.getStatus()).isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_ACCEPTANCE);
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(outcomes).allMatch(Outcome::successful);
    }

    private UserAccount createParamedic() {
        Organization organization = organizationRepository.save(Organization.create(
                "경합 테스트 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "racesearchmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                account,
                organization,
                "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private UserAccount createHospital() {
        Organization organization = organizationRepository.save(Organization.create(
                "경합 테스트 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "racesearchhospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 테스트 주소",
                new BigDecimal("37.6021000"),
                new BigDecimal("127.0105000"),
                "02-0000-0000"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount authenticated(UserAccount account, UserRole role) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                role
        );
    }

    private List<Outcome> runTogether(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstResult = executor.submit(waitThenRun(ready, start, first));
            Future<Outcome> secondResult = executor.submit(waitThenRun(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private Callable<Outcome> waitThenRun(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<Outcome> delegate
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return delegate.call();
        };
    }

    private record Outcome(boolean successful, String errorCode) {

        static Outcome success() {
            return new Outcome(true, null);
        }

        static Outcome failure(String errorCode) {
            return new Outcome(false, errorCode);
        }
    }
}
