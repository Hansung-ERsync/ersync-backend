package com.hansungteam.ersync.hospital.search;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.RouteEstimateCoordinator;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferView;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferEventRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalSearchRoundRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HospitalSearchIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalSearchRoundRepository roundRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private HospitalOfferEventRepository offerEventRepository;
    @Autowired private RealtimeOutboxEventRepository outboxEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private HospitalOfferService hospitalOfferService;
    @Autowired private RouteEstimateCoordinator routeEstimateCoordinator;

    @Test
    void initialSearchExpandsImmediatelyUntilFourHospitalsAreWithinThirtyKilometers() {
        UserAccount paramedic = createParamedic("searchmedic1");
        createHospital("searchhospital1", "37.6321000", ReceivingStatus.ON, true);
        createHospital("searchhospital2", "37.7171000", ReceivingStatus.ON, true);
        createHospital("searchhospital3", "37.8071000", ReceivingStatus.ON, true);
        createHospital("searchhospital4", "37.8421000", ReceivingStatus.ON, true);
        createHospital("searchhospitaloff", "37.6021000", ReceivingStatus.OFF, true);
        createHospital("searchhospitalinactive", "37.6021000", ReceivingStatus.ON, false);

        var creation = transportRequestService.create(
                authenticated(paramedic),
                "hospital-search-request-1",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();

        assertThat(creation.response().status()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(attempt.getCurrentRadiusKm()).isZero();
        assertThat(offerRepository.count()).isZero();

        hospitalSearchService.processDueAttempt(attempt.getId());

        var storedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        var rounds = roundRepository.findByDispatchAttemptIdOrderByRadiusKmAsc(attempt.getId());
        var offers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId());
        assertThat(rounds).extracting(round -> round.getRadiusKm()).containsExactly(10, 20, 30);
        assertThat(rounds).extracting(round -> round.getCandidateCount()).containsExactly(1, 2, 4);
        assertThat(rounds).extracting(round -> round.getNewOfferCount()).containsExactly(0, 0, 4);
        assertThat(offers).hasSize(4);
        assertThat(offers).allMatch(offer -> offer.getStatus() == HospitalOfferStatus.PENDING);
        assertThat(offers).allMatch(offer -> offer.getStraightLineDistanceMeters() <= 30_000L);
        assertThat(storedAttempt.getCurrentRadiusKm()).isEqualTo(30);
        assertThat(storedAttempt.isCandidateShortage()).isFalse();
        assertThat(storedAttempt.getNextExpansionAt()).isEqualTo(rounds.getLast().getResponseDeadlineAt());
        assertThat(offerEventRepository.findByHospitalOfferDispatchAttemptIdOrderByOccurredAtAsc(attempt.getId()))
                .hasSize(4);
        assertThat(outboxEventRepository.count()).isEqualTo(4);
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_OFFER_CREATED)).isEqualTo(4);
    }

    @Test
    void noEligibleHospitalWaitsAtOneHundredKilometersWithoutCreatingOffer() {
        UserAccount paramedic = createParamedic("searchmedic2");

        var creation = transportRequestService.create(
                authenticated(paramedic),
                "hospital-search-request-2",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();

        hospitalSearchService.processDueAttempt(attempt.getId());

        var storedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        var storedRequest = transportRequestRepository.findByPublicId(
                creation.response().transportRequestId()
        ).orElseThrow();
        var rounds = roundRepository.findByDispatchAttemptIdOrderByRadiusKmAsc(attempt.getId());
        assertThat(rounds).extracting(round -> round.getRadiusKm())
                .containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        assertThat(rounds).allMatch(round -> round.getCandidateCount() == 0);
        assertThat(storedAttempt.getStatus()).isEqualTo(HospitalDispatchAttemptStatus.SEARCHING);
        assertThat(storedAttempt.getCurrentRadiusKm()).isEqualTo(100);
        assertThat(storedAttempt.isCandidateShortage()).isTrue();
        assertThat(storedAttempt.getNextExpansionAt()).isNull();
        assertThat(storedAttempt.getEndedAt()).isNull();
        assertThat(storedRequest.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(offerRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_SEARCH_EXHAUSTED)).isZero();
    }

    @Test
    void dueSearchExpandsByTenKilometersAndOffersOnlyToNewHospital() {
        UserAccount paramedic = createParamedic("searchmedic3");
        createHospital("expansionhospital1", "37.6021000", ReceivingStatus.ON, true);
        createHospital("expansionhospital2", "37.6221000", ReceivingStatus.ON, true);
        createHospital("expansionhospital3", "37.6421000", ReceivingStatus.ON, true);
        createHospital("expansionhospital4", "37.7171000", ReceivingStatus.ON, true);

        var creation = transportRequestService.create(
                authenticated(paramedic),
                "hospital-search-request-3",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        assertThat(attempt.getCurrentRadiusKm()).isEqualTo(10);
        assertThat(offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId())).hasSize(3);

        attempt.scheduleNextExpansion(10, false, Instant.EPOCH);
        hospitalSearchService.processDueAttempt(attempt.getId());

        var rounds = roundRepository.findByDispatchAttemptIdOrderByRadiusKmAsc(attempt.getId());
        var offers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId());
        assertThat(rounds).extracting(round -> round.getRadiusKm()).containsExactly(10, 20);
        assertThat(rounds).extracting(round -> round.getNewOfferCount()).containsExactly(3, 1);
        assertThat(offers).hasSize(4);
        assertThat(offers).extracting(offer -> offer.getHospitalProfile().getAccount().getLoginId())
                .doesNotHaveDuplicates();
        assertThat(attempt.getCurrentRadiusKm()).isEqualTo(20);
    }

    @Test
    void finalResponseWindowKeepsPendingOfferAndSearchOpen() {
        UserAccount paramedic = createParamedic("searchmedic4");
        UserAccount hospital = createHospital("timeouthospital", "37.6021000", ReceivingStatus.ON, true);

        var creation = transportRequestService.create(
                authenticated(paramedic),
                "hospital-search-request-4",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        assertThat(attempt.getCurrentRadiusKm()).isEqualTo(100);
        assertThat(attempt.isCandidateShortage()).isTrue();

        attempt.scheduleNextExpansion(100, true, Instant.EPOCH);
        hospitalSearchService.processDueAttempt(attempt.getId());

        var offer = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId()).getFirst();
        var request = transportRequestRepository.findByPublicId(creation.response().transportRequestId())
                .orElseThrow();
        assertThat(offer.getStatus()).isEqualTo(HospitalOfferStatus.PENDING);
        assertThat(offer.getClosedAt()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(HospitalDispatchAttemptStatus.SEARCHING);
        assertThat(attempt.getNextExpansionAt()).isNull();
        assertThat(attempt.getEndedAt()).isNull();
        assertThat(request.getStatus()).isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_OFFER_NO_RESPONSE)).isZero();
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_SEARCH_EXHAUSTED)).isZero();

        var active = hospitalOfferService.list(
                hospitalAuthenticated(hospital),
                HospitalOfferView.ACTIVE,
                0,
                20
        );
        assertThat(active.items()).singleElement().satisfies(item -> {
            assertThat(item.hospitalOutcome().name()).isEqualTo("AWAITING_RESPONSE");
            assertThat(item.processedAt()).isNull();
        });
    }

    @Test
    void missingLocalNaverCredentialsOnlyMakesEtaUnavailable() {
        UserAccount paramedic = createParamedic("searchmedic5");
        createHospital("etanokeyhospital", "37.6021000", ReceivingStatus.ON, true);

        var creation = transportRequestService.create(
                authenticated(paramedic),
                "hospital-search-request-5",
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        var offer = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId()).getFirst();

        routeEstimateCoordinator.process(offer.getId());

        var storedOffer = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(storedOffer.getRouteEstimateStatus().name()).isEqualTo("UNAVAILABLE");
        assertThat(storedOffer.getStatus()).isEqualTo(HospitalOfferStatus.PENDING);
        assertThat(storedOffer.getRouteDistanceMeters()).isNull();
        assertThat(storedOffer.getEtaSeconds()).isNull();
        assertThat(outboxEventRepository.count()).isEqualTo(3);
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
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

    private UserAccount createHospital(
            String loginId,
            String latitude,
            ReceivingStatus receivingStatus,
            boolean active
    ) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 병원",
                OrganizationType.HOSPITAL
        ));
        if (!active) {
            organization.deactivate();
        }
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 테스트 주소",
                new BigDecimal(latitude),
                new BigDecimal("127.0105000"),
                "02-0000-0000"
        );
        profile.changeReceivingStatus(receivingStatus);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount authenticated(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                UserRole.PARAMEDIC
        );
    }

    private AuthenticatedAccount hospitalAuthenticated(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
    }
}
