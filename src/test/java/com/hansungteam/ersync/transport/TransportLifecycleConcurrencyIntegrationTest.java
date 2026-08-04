package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
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
import com.hansungteam.ersync.transport.api.CancelTransportRequestRequest;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportLifecycleCommandRepository;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransportLifecycleConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportLifecycleCommandRepository lifecycleCommandRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private TransportLifecycleService lifecycleService;
    @Autowired private TransportLocationService locationService;

    @Test
    void cancellationAndAcceptanceRaceEndsInOneConsistentCancelledRequest() throws Exception {
        UserAccount paramedic = createParamedic("cancelracemedic");
        UserAccount hospital = createHospital("cancelracehospital", "37.6021000");
        String requestId = createAndSearch(paramedic, "cancel-accept-concurrency-request");
        HospitalOffer offer = offerFor("cancelracehospital 병원", requestId);

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.cancel(
                        paramedicPrincipal(paramedic),
                        requestId,
                        "cancel-accept-concurrency-cancel",
                        new CancelTransportRequestRequest(
                                TransportCancellationReason.PATIENT_REFUSED_TRANSPORT, null
                        )
                )),
                () -> capture(() -> offerService.accept(
                        hospitalPrincipal(hospital), offer.getPublicId(), "cancel-accept-concurrency-accept"
                ))
        );

        assertThat(outcomes).anyMatch(Outcome::successful);
        assertThat(outcomes.stream().filter(Outcome::successful).count()).isBetween(1L, 2L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode).toList())
                .allMatch(code -> code.equals("TRANSPORT_006"));
        var storedRequest = requestRepository.findByPublicId(requestId).orElseThrow();
        var storedOffer = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.CANCELLED);
        assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
        assertThat(storedOffer.getClosedAt()).isNotNull();
        assertThat(storedOffer.getStatus()).isIn(HospitalOfferStatus.PENDING, HospitalOfferStatus.ACCEPTED);
        assertThat(lifecycleCommandRepository.countByTransportRequestId(storedRequest.getId())).isEqualTo(1);
    }

    @Test
    void handoffRequestAndDestinationWithdrawalAllowOnlyTheFirstTransition() throws Exception {
        UserAccount paramedic = createParamedic("handoffracemedic");
        UserAccount hospital = createHospital("handoffracehospital", "37.6021000");
        String requestId = createAndSearch(paramedic, "handoff-withdraw-concurrency-request");
        HospitalOffer offer = offerFor("handoffracehospital 병원", requestId);
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "handoff-withdraw-accept");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "handoff-withdraw-select", offer.getPublicId()
        );

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.requestHandoff(
                        paramedicPrincipal(paramedic), requestId, "handoff-withdraw-request"
                )),
                () -> capture(() -> offerService.withdrawAcceptance(
                        hospitalPrincipal(hospital),
                        offer.getPublicId(),
                        "handoff-withdraw-withdrawal",
                        new WithdrawHospitalAcceptanceRequest(
                                HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null
                        )
                ))
        );

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode).toList())
                .containsExactly("TRANSPORT_004");
        var storedRequest = requestRepository.findByPublicId(requestId).orElseThrow();
        var storedOffer = offerRepository.findById(offer.getId()).orElseThrow();
        if (storedRequest.getStatus() == TransportRequestStatus.HANDOFF_REQUESTED) {
            assertThat(storedRequest.getCurrentDestinationOffer()).isNotNull();
            assertThat(storedOffer.getStatus()).isEqualTo(HospitalOfferStatus.ACCEPTED);
            assertThat(lifecycleCommandRepository.countByTransportRequestId(storedRequest.getId())).isEqualTo(1);
        } else {
            assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
            assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
            assertThat(storedOffer.getStatus()).isEqualTo(HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
            assertThat(lifecycleCommandRepository.countByTransportRequestId(storedRequest.getId())).isZero();
        }
    }

    @Test
    void handoffConfirmationAndLocationUpdateNeverModifyAfterCompletion() throws Exception {
        UserAccount paramedic = createParamedic("confirmracemedic");
        UserAccount hospital = createHospital("confirmracehospital", "37.6021000");
        String requestId = createAndSearch(paramedic, "confirm-location-concurrency-request");
        HospitalOffer offer = offerFor("confirmracehospital 병원", requestId);
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "confirm-location-accept");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "confirm-location-select", offer.getPublicId()
        );
        lifecycleService.requestHandoff(
                paramedicPrincipal(paramedic), requestId, "confirm-location-handoff-request"
        );

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.confirmHandoff(
                        hospitalPrincipal(hospital), offer.getPublicId(), "confirm-location-confirm"
                )),
                () -> capture(() -> locationService.update(
                        paramedicPrincipal(paramedic),
                        requestId,
                        "confirm-location-update",
                        new UpdateTransportLocationRequest(
                                new BigDecimal("37.6000000"),
                                new BigDecimal("127.1000000"),
                                Instant.parse("2026-08-04T12:00:00Z")
                        )
                ))
        );

        assertThat(outcomes.getFirst().successful()).isTrue();
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode).toList())
                .allMatch(code -> code.equals("TRANSPORT_004"));
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.COMPLETED);

        Outcome laterLocation = capture(() -> locationService.update(
                paramedicPrincipal(paramedic),
                requestId,
                "confirm-location-after-completion",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7000000"),
                        new BigDecimal("127.2000000"),
                        Instant.parse("2026-08-04T12:01:00Z")
                )
        ));
        assertThat(laterLocation.successful()).isFalse();
        assertThat(laterLocation.errorCode()).isEqualTo("TRANSPORT_004");
    }

    private List<Outcome> runTogether(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> firstFuture = executor.submit(awaitThenCall(ready, start, first));
            Future<Outcome> secondFuture = executor.submit(awaitThenCall(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstFuture.get(10, TimeUnit.SECONDS),
                    secondFuture.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<Outcome> awaitThenCall(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<Outcome> command
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            return command.call();
        };
    }

    private Outcome capture(Runnable action) {
        try {
            action.run();
            return new Outcome(true, null);
        } catch (CustomException exception) {
            return new Outcome(false, exception.getErrorCode().getCode());
        }
    }

    private String createAndSearch(UserAccount paramedic, String idempotencyKey) {
        String requestId = requestService.create(
                paramedicPrincipal(paramedic), idempotencyKey, ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        searchService.processDueAttempt(attempt.getId());
        return requestId;
    }

    private HospitalOffer offerFor(String hospitalName, String requestId) {
        return offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(offer -> offer.getHospitalNameSnapshot().equals(hospitalName))
                .findFirst()
                .orElseThrow();
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0001"));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private UserAccount createHospital(String loginId, String latitude) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 병원", OrganizationType.HOSPITAL
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 테스트 주소",
                new BigDecimal(latitude),
                new BigDecimal("127.0105000"),
                "02-0000-0000"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount paramedicPrincipal(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(), account.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
    }

    private AuthenticatedAccount hospitalPrincipal(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(), account.getOrganization().getPublicId(), UserRole.HOSPITAL_STAFF
        );
    }

    private record Outcome(boolean successful, String errorCode) {
    }
}
