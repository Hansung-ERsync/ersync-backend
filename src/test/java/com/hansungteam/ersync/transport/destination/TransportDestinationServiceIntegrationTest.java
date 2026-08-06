package com.hansungteam.ersync.transport.destination;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.application.TransportHospitalSearchService;
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptTrigger;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransportDestinationServiceIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportDestinationCommandRepository commandRepository;
    @Autowired private RealtimeOutboxEventRepository outboxRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private HospitalReceivingService hospitalReceivingService;
    @Autowired private TransportHospitalSearchService transportHospitalSearchService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private MockMvc mockMvc;

    @Test
    void selectsChangesAndReplaysOneCurrentDestination() throws Exception {
        UserAccount paramedic = createParamedic("destinationmedic");
        UserAccount hospitalOne = createHospital("destinationhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("destinationhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();

        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-destination-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-destination-2");

        var selected = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-1", offerOne.getPublicId()
        );
        assertThat(selected.resultType()).isEqualTo(TransportDestinationResultType.SELECTED);
        assertThat(selected.transportRequestStatus()).isEqualTo(TransportRequestStatus.EN_ROUTE);
        assertThat(selected.previousDestinationOfferId()).isNull();

        var changed = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-2", offerTwo.getPublicId()
        );
        assertThat(changed.resultType()).isEqualTo(TransportDestinationResultType.CHANGED);
        assertThat(changed.previousDestinationOfferId()).isEqualTo(offerOne.getPublicId());

        var replay = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-2", offerTwo.getPublicId()
        );
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(commandRepository.count()).isEqualTo(2);

        var unchanged = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-3", offerTwo.getPublicId()
        );
        assertThat(unchanged.resultType()).isEqualTo(TransportDestinationResultType.UNCHANGED);
        assertThat(commandRepository.count()).isEqualTo(3);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow()
                .getCurrentDestinationOffer().getPublicId()).isEqualTo(offerTwo.getPublicId());

        Long transportRequestId = requestRepository.findByPublicId(requestId).orElseThrow().getId();
        var latestEffectiveDestinations = commandRepository.findLatestEffectiveDestinations(
                Set.of(transportRequestId)
        );
        assertThat(latestEffectiveDestinations).singleElement().satisfies(latest -> {
            assertThat(latest.getTransportRequestId()).isEqualTo(transportRequestId);
            assertThat(latest.getDestinationOfferId()).isEqualTo(offerTwo.getId());
            assertThat(latest.getOccurredAt()).isEqualTo(changed.changedAt());
        });

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").value(changed.changedAt().toString()));

        var changedBack = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-4", offerOne.getPublicId()
        );
        assertThat(changedBack.resultType()).isEqualTo(TransportDestinationResultType.CHANGED);
        assertThat(changedBack.previousDestinationOfferId()).isEqualTo(offerTwo.getPublicId());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").value(changedBack.changedAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(true));

        assertThatThrownBy(() -> destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-2", offerOne.getPublicId()
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_DUPLICATE_CONFLICT));
    }

    @Test
    void destinationApiReturnsAuthoritativeDestinationAndCreatesMinimalSignals() throws Exception {
        UserAccount paramedic = createParamedic("destinationapimedic");
        UserAccount hospitalOne = createHospital("destinationapihospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("destinationapihospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-api-destination-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-api-destination-2");

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/destination", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "destination-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":\"" + offerOne.getPublicId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestStatus").value("EN_ROUTE"))
                .andExpect(jsonPath("$.selectedDestinationOfferId").value(offerOne.getPublicId()))
                .andExpect(jsonPath("$.resultType").value("SELECTED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDestinationOfferId").value(offerOne.getPublicId()))
                .andExpect(jsonPath("$.offers[?(@.offerId == '" + offerOne.getPublicId()
                        + "')].currentDestination").value(true));

        assertThat(outboxRepository.countByEventType(RealtimeEventType.DESTINATION_SELECTED)).isEqualTo(3);
        assertThat(auditRepository.countByAction(AuditAction.DESTINATION_SELECTED)).isEqualTo(1);
    }

    @Test
    void nonDestinationWithdrawalKeepsDestinationAndIsIdempotent() throws Exception {
        UserAccount paramedic = createParamedic("withdrawmedic");
        UserAccount hospitalOne = createHospital("withdrawhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("withdrawhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-withdraw-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-withdraw-2");
        destinationService.select(paramedicPrincipal(paramedic), requestId, "select-withdraw-key", offerOne.getPublicId());

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists())
                .andExpect(jsonPath("$.items[0].canWithdraw").value(true))
                .andExpect(jsonPath("$.items[0].ageStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].routeEstimateStatus").doesNotExist());
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo))
                        .header("Idempotency-Key", "withdraw-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"전문의 상황 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.transportRequestStatus").value("EN_ROUTE"))
                .andExpect(jsonPath("$.currentDestinationOfferId").value(offerOne.getPublicId()))
                .andExpect(jsonPath("$.searchRestarted").value(false))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.items[0].processedAt").exists())
                .andExpect(jsonPath("$.items[0].canWithdraw").value(false))
                .andExpect(jsonPath("$.items[0].withdrawalReason").value("OTHER"))
                .andExpect(jsonPath("$.items[0].withdrawalDetail").value("전문의 상황 변경"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo))
                        .header("Idempotency-Key", "withdraw-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"전문의 상황 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow()
                .getCurrentDestinationOffer().getPublicId()).isEqualTo(offerOne.getPublicId());
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        assertThat(attemptRepository.findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow().getAttemptNumber()).isEqualTo(1);
        assertThat(outboxRepository.countByEventType(RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN))
                .isEqualTo(2);
        assertThat(auditRepository.countByAction(AuditAction.HOSPITAL_ACCEPTANCE_WITHDRAWN)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo))
                        .header("Idempotency-Key", "withdraw-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"다른 사유\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));
    }

    @Test
    void currentDestinationWithdrawalClearsDestinationAndKeepsOtherAcceptance() throws Exception {
        UserAccount paramedic = createParamedic("currentwithdrawmedic");
        UserAccount hospitalOne = createHospital("currentwithdrawhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("currentwithdrawhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-current-withdraw-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-current-withdraw-2");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "select-current-withdraw", offerOne.getPublicId()
        );
        UserAccount newlyEligibleHospital = createHospital(
                "currentwithdrawhospital3", "37.6221000"
        );

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne))
                        .header("Idempotency-Key", "withdraw-current-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPECIALIST_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestStatus").value("ACCEPTED_AVAILABLE"))
                .andExpect(jsonPath("$.currentDestinationOfferId").doesNotExist())
                .andExpect(jsonPath("$.searchRestarted").value(true));

        var stored = requestRepository.findByPublicId(requestId).orElseThrow();
        assertThat(stored.getCurrentDestinationOffer()).isNull();
        assertThat(stored.getStatus()).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);

        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        assertThat(recovery.getAttemptNumber()).isEqualTo(2);
        assertThat(recovery.getTriggerType()).isEqualTo(HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL);
        assertThat(recovery.getSearchOriginLatitude()).isEqualByComparingTo(stored.getOriginLatitude());
        assertThat(recovery.getSearchOriginLongitude()).isEqualByComparingTo(stored.getOriginLongitude());

        searchService.processDueAttempt(recovery.getId());

        var recoveryOffers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(recovery.getId());
        assertThat(recoveryOffers).hasSize(1);
        assertThat(recoveryOffers.getFirst().getHospitalProfile().getAccount().getId())
                .isEqualTo(newlyEligibleHospital.getId());
        assertThat(recoveryOffers)
                .noneMatch(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .noneMatch(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()));

        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "select-after-withdraw", offerTwo.getPublicId()
        );
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_DESTINATION);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.EN_ROUTE);
    }

    @Test
    void withdrawnHospitalContactRemainsHiddenAfterRecoverySearchExhaustion() throws Exception {
        UserAccount paramedic = createParamedic("withdrawncontactmedic");
        UserAccount hospital = createHospital("withdrawncontacthospital", "37.6021000");
        String requestId = createAndSearch(paramedic);
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId().equals(hospital.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "accept-withdrawn-contact");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "select-withdrawn-contact", offer.getPublicId()
        );

        offerService.withdrawAcceptance(
                hospitalPrincipal(hospital),
                offer.getPublicId(),
                "withdraw-contact-key",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        searchService.processDueAttempt(recovery.getId());

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANDIDATES_EXHAUSTED);
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].status").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.offers[0].hospitalContact").doesNotExist());
    }

    @Test
    void lateAcceptanceAfterWithdrawalRecoveryExhaustionRestoresSelectableState() {
        UserAccount paramedic = createParamedic("lateacceptancemedic");
        UserAccount hospitalOne = createHospital("lateacceptancehospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("lateacceptancehospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "late-acceptance-one");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "late-acceptance-select-one", offerOne.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "late-acceptance-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        searchService.processDueAttempt(recovery.getId());
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANDIDATES_EXHAUSTED);

        var lateAcceptance = offerService.accept(
                hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "late-acceptance-two"
        );

        assertThat(lateAcceptance.transportRequestStatus()).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        var selected = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "late-acceptance-select-two", offerTwo.getPublicId()
        );
        assertThat(selected.transportRequestStatus()).isEqualTo(TransportRequestStatus.EN_ROUTE);
        assertThat(selected.selectedDestinationOfferId()).isEqualTo(offerTwo.getPublicId());
    }

    @Test
    void lateAcceptanceStopsManualRetryStartedAfterWithdrawalRecoveryExhaustion() {
        UserAccount paramedic = createParamedic("lateretrymedic");
        UserAccount hospitalOne = createHospital("lateretryhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("lateretryhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "late-retry-accept-one");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "late-retry-select-one", offerOne.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "late-retry-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        searchService.processDueAttempt(recovery.getId());
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANDIDATES_EXHAUSTED);
        transportHospitalSearchService.retry(
                paramedicPrincipal(paramedic), requestId, "late-retry-command-key"
        );
        var manualRetry = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        assertThat(manualRetry.getStatus()).isEqualTo(HospitalDispatchAttemptStatus.SEARCHING);

        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "late-retry-accept-two");

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(attemptRepository.findById(manualRetry.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_ACCEPTANCE);
    }

    @Test
    void receivingOffKeepsCurrentDestinationAndExistingWithdrawalAuthority() {
        UserAccount paramedic = createParamedic("receivingoffmedic");
        UserAccount hospital = createHospital("receivingoffhospital", "37.6021000");
        String requestId = createAndSearch(paramedic);
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId().equals(hospital.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "receiving-off-accept");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "receiving-off-select", offer.getPublicId()
        );

        hospitalReceivingService.change(hospitalPrincipal(hospital), ReceivingStatus.OFF);

        var detail = offerService.detail(hospitalPrincipal(hospital), offer.getPublicId());
        assertThat(detail.currentDestination()).isTrue();
        assertThat(detail.canWithdraw()).isTrue();
        assertThat(detail.transportRequestStatus()).isEqualTo(TransportRequestStatus.EN_ROUTE);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getCurrentDestinationOffer())
                .isNotNull();

        var withdrawn = offerService.withdrawAcceptance(
                hospitalPrincipal(hospital),
                offer.getPublicId(),
                "receiving-off-withdraw",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        assertThat(withdrawn.searchRestarted()).isTrue();
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getCurrentDestinationOffer())
                .isNull();
    }

    @Test
    void destinationAndWithdrawalEnforceOwnerRoleOrganizationAndState() throws Exception {
        UserAccount owner = createParamedic("scopeownermedic");
        UserAccount otherParamedic = createParamedic("scopeothermedic");
        UserAccount hospital = createHospital("scopehospital1", "37.6021000");
        UserAccount otherHospital = createHospital("scopehospital2", "37.6121000");
        String requestId = createAndSearch(owner);
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId().equals(hospital.getId()))
                .findFirst().orElseThrow();
        var acceptedOffer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId().equals(otherHospital.getId()))
                .findFirst().orElseThrow();
        offerService.accept(
                hospitalPrincipal(otherHospital), acceptedOffer.getPublicId(), "scope-accept-other-hospital"
        );

        String body = "{\"offerId\":\"" + offer.getPublicId() + "\"}";
        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/destination", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherParamedic))
                        .header("Idempotency-Key", "scope-destination-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/destination", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "scope-destination-2")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_002"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/destination", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "scope-destination-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherHospital))
                        .header("Idempotency-Key", "scope-withdraw-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BED_SHORTAGE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "scope-withdraw-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "scope-withdraw-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BED_SHORTAGE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_006"));
    }

    private String createAndSearch(UserAccount paramedic) {
        String requestId = requestService.create(
                paramedicPrincipal(paramedic),
                "destination-request-key",
                ValidTransportRequestFixtures.request()
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

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
