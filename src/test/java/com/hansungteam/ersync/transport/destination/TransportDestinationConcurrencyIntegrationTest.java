package com.hansungteam.ersync.transport.destination;

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
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptTrigger;
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
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
class TransportDestinationConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportDestinationCommandRepository commandRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private TransportDestinationService destinationService;

    @Test
    void concurrentDifferentDestinationCommandsRemainSerialized() throws Exception {
        UserAccount paramedic = createParamedic("concurrentdestinationmedic");
        UserAccount hospitalOne = createHospital("concurrentdestinationhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("concurrentdestinationhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "concurrent-destination-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("concurrentdestinationhospital1 병원"))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("concurrentdestinationhospital2 병원"))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "concurrent-accept-one");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "concurrent-accept-two");

        List<Outcome> outcomes = runTogether(
                () -> destinationOutcome(paramedic, requestId, "concurrent-destination-one", offerOne.getPublicId()),
                () -> destinationOutcome(paramedic, requestId, "concurrent-destination-two", offerTwo.getPublicId())
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        var stored = requestRepository.findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TransportRequestStatus.EN_ROUTE);
        assertThat(stored.getCurrentDestinationOffer().getPublicId())
                .isIn(offerOne.getPublicId(), offerTwo.getPublicId());
        assertThat(commandRepository.countByTransportRequestId(stored.getId())).isEqualTo(2);
    }

    @Test
    void destinationSelectionAndAcceptanceWithdrawalCannotCreateMixedState() throws Exception {
        UserAccount paramedic = createParamedic("selectionwithdrawalmedic");
        UserAccount hospital = createHospital("selectionwithdrawalhospital", "37.6021000");
        String requestId = createAndSearch(paramedic, "selection-withdrawal-request");
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalNameSnapshot().equals("selectionwithdrawalhospital 병원"))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "selection-withdrawal-accept");

        List<Outcome> outcomes = runTogether(
                () -> destinationOutcome(
                        paramedic, requestId, "selection-withdrawal-destination", offer.getPublicId()
                ),
                () -> withdrawalOutcome(hospital, offer.getPublicId())
        );

        assertThat(outcomes).anyMatch(Outcome::successful);
        var storedOffer = offerRepository.findById(offer.getId()).orElseThrow();
        var storedRequest = requestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow();
        assertThat(storedOffer.getStatus()).isEqualTo(HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(attemptRepository.findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow().getAttemptNumber()).isEqualTo(2);
        assertThat(commandRepository.countByTransportRequestId(storedRequest.getId())).isBetween(0L, 1L);
    }

    @Test
    void secondAcceptanceWithdrawalReusesRecoveryAlreadyStartedByCurrentDestinationWithdrawal() {
        UserAccount paramedic = createParamedic("sequentialrecoverymedic");
        UserAccount hospitalOne = createHospital("sequentialrecoveryhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("sequentialrecoveryhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "sequential-recovery-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("sequentialrecoveryhospital1 병원"))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("sequentialrecoveryhospital2 병원"))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "sequential-accept-one");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "sequential-accept-two");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "sequential-select-one", offerOne.getPublicId()
        );

        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "sequential-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalTwo),
                offerTwo.getPublicId(),
                "sequential-withdraw-two",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );

        var storedRequest = requestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow();
        List<Long> activeRecoveryIds = attemptRepository.findLatestIdsByTransportRequestIdAndStatus(
                storedRequest.getId(),
                HospitalDispatchAttemptStatus.SEARCHING,
                PageRequest.of(0, 10)
        );
        assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(activeRecoveryIds).hasSize(1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void fourHospitalsAtThirtyKilometersRecoverWhenSecondSelectionAndWithdrawalRace() throws Exception {
        UserAccount paramedic = createParamedic("thirtykilometermedic");
        UserAccount hospitalOne = createHospital("thirtykilometerhospital1", "37.7900000");
        UserAccount hospitalTwo = createHospital("thirtykilometerhospital2", "37.8000000");
        createHospital("thirtykilometerhospital3", "37.8100000");
        createHospital("thirtykilometerhospital4", "37.8200000");
        createHospital("fortykilometerhospital5", "37.9000000");
        String requestId = createAndSearch(paramedic, "thirty-kilometer-scenario-request");
        var initialAttempt = attemptRepository
                .findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        var initialOffers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(initialAttempt.getId());

        assertThat(initialAttempt.getCurrentRadiusKm()).isEqualTo(30);
        assertThat(initialOffers).hasSize(4);
        assertThat(initialOffers).noneMatch(offer ->
                offer.getHospitalNameSnapshot().equals("fortykilometerhospital5 병원")
        );

        var offerOne = initialOffers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("thirtykilometerhospital1 병원")
        ).findFirst().orElseThrow();
        var offerTwo = initialOffers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("thirtykilometerhospital2 병원")
        ).findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "scenario-accept-one");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "scenario-accept-two");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "scenario-select-one", offerOne.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "scenario-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );

        List<Outcome> outcomes = runTogether(
                () -> destinationOutcome(paramedic, requestId, "scenario-select-two", offerTwo.getPublicId()),
                () -> withdrawalOutcome(hospitalTwo, offerTwo.getPublicId())
        );

        assertThat(outcomes.get(1).successful()).isTrue();
        assertThat(outcomes.getFirst().successful()
                || "TRANSPORT_002".equals(outcomes.getFirst().errorCode())
                || "TRANSPORT_004".equals(outcomes.getFirst().errorCode())).isTrue();
        var storedRequest = requestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow();
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);

        List<Long> activeRecoveryIds = attemptRepository.findLatestIdsByTransportRequestIdAndStatus(
                storedRequest.getId(), HospitalDispatchAttemptStatus.SEARCHING, PageRequest.of(0, 10)
        );
        assertThat(activeRecoveryIds).hasSize(1);
        var activeRecovery = attemptRepository.findById(activeRecoveryIds.getFirst()).orElseThrow();
        assertThat(activeRecovery.getTriggerType()).isEqualTo(HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL);

        searchService.processDueAttempt(activeRecovery.getId());

        var recoveryOffers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(activeRecovery.getId());
        var processedRecovery = attemptRepository.findById(activeRecovery.getId()).orElseThrow();
        assertThat(processedRecovery.getCurrentRadiusKm()).isEqualTo(100);
        assertThat(processedRecovery.isCandidateShortage()).isTrue();
        assertThat(recoveryOffers).hasSize(1);
        assertThat(recoveryOffers.getFirst().getHospitalNameSnapshot())
                .isEqualTo("fortykilometerhospital5 병원");
    }

    @Test
    void withdrawalImmediatelyAfterSecondDestinationSelectionStartsOneReplacementRecovery() {
        UserAccount paramedic = createParamedic("immediatewithdrawalmedic");
        UserAccount hospitalOne = createHospital("immediatewithdrawalhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("immediatewithdrawalhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "immediate-withdrawal-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("immediatewithdrawalhospital1 병원"))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("immediatewithdrawalhospital2 병원"))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "immediate-accept-one");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "immediate-accept-two");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "immediate-select-one", offerOne.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "immediate-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var firstRecovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();

        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "immediate-select-two", offerTwo.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalTwo),
                offerTwo.getPublicId(),
                "immediate-withdraw-two",
                new WithdrawHospitalAcceptanceRequest(
                        HospitalAcceptanceWithdrawalReason.SPECIALIST_UNAVAILABLE,
                        null
                )
        );

        var storedRequest = requestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow();
        assertThat(storedRequest.getCurrentDestinationOffer()).isNull();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(attemptRepository.findById(firstRecovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_DESTINATION);
        List<Long> activeRecoveryIds = attemptRepository.findLatestIdsByTransportRequestIdAndStatus(
                storedRequest.getId(), HospitalDispatchAttemptStatus.SEARCHING, PageRequest.of(0, 10)
        );
        assertThat(activeRecoveryIds).hasSize(1);
        assertThat(activeRecoveryIds.getFirst()).isNotEqualTo(firstRecovery.getId());
        assertThat(attemptRepository.findById(activeRecoveryIds.getFirst()).orElseThrow().getTriggerType())
                .isEqualTo(HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void withdrawalRecoveryExhaustionAndOlderPendingAcceptanceRemainConsistent() throws Exception {
        UserAccount paramedic = createParamedic("recoveryraceparamedic");
        UserAccount hospitalOne = createHospital("recoveryracehospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("recoveryracehospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "recovery-race-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("recoveryracehospital1 병원"))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream().filter(offer ->
                offer.getHospitalNameSnapshot().equals("recoveryracehospital2 병원"))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "recovery-race-accept-one");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "recovery-race-select-one", offerOne.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "recovery-race-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();

        List<Outcome> outcomes = runTogether(
                () -> {
                    searchService.processDueAttempt(recovery.getId());
                    return Outcome.success();
                },
                () -> {
                    offerService.accept(
                            hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "recovery-race-accept-two"
                    );
                    return Outcome.success();
                }
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalOfferStatus.ACCEPTED);
        assertThat(requestRepository.findByPublicIdAndOwnerAccountPublicId(requestId, paramedic.getPublicId())
                .orElseThrow().getStatus()).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.EXHAUSTED);
    }

    private Outcome destinationOutcome(
            UserAccount paramedic,
            String requestId,
            String key,
            String offerId
    ) {
        try {
            destinationService.select(paramedicPrincipal(paramedic), requestId, key, offerId);
            return Outcome.success();
        } catch (CustomException exception) {
            return Outcome.failure(exception.getErrorCode().getCode());
        }
    }

    private Outcome withdrawalOutcome(UserAccount hospital, String offerId) {
        try {
            offerService.withdrawAcceptance(
                    hospitalPrincipal(hospital),
                    offerId,
                    "selection-withdrawal-command",
                    new WithdrawHospitalAcceptanceRequest(
                            HospitalAcceptanceWithdrawalReason.SPECIALIST_UNAVAILABLE,
                            null
                    )
            );
            return Outcome.success();
        } catch (CustomException exception) {
            return Outcome.failure(exception.getErrorCode().getCode());
        }
    }

    private String createAndSearch(UserAccount paramedic, String key) {
        String requestId = requestService.create(
                paramedicPrincipal(paramedic), key, ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        searchService.processDueAttempt(attempt.getId());
        return requestId;
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
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
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
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
