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
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.api.RejectHospitalOfferRequest;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEventType;
import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptTrigger;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferEventRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
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
    @Autowired private HospitalOfferEventRepository offerEventRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportDestinationCommandRepository commandRepository;
    @Autowired private RealtimeOutboxEventRepository outboxRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private HospitalReceivingService hospitalReceivingService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private MockMvc mockMvc;

    @Test
    void selectsChangesAndReplaysOneCurrentDestination() throws Exception {
        UserAccount paramedic = createParamedic("destinationmedic");
        UserAccount hospitalOne = createHospital("destinationhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("destinationhospital2", "37.6121000");
        UserAccount hospitalThree = createHospital("destinationhospital3", "37.6221000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        var offerThree = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalThree.getId()))
                .findFirst().orElseThrow();

        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-destination-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-destination-2");

        var selected = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-1", offerOne.getPublicId()
        );
        assertThat(selected.resultType()).isEqualTo(TransportDestinationResultType.SELECTED);
        assertThat(selected.transportRequestStatus()).isEqualTo(TransportRequestStatus.EN_ROUTE);
        assertThat(selected.previousDestinationOfferId()).isNull();
        assertThat(offerRepository.findById(offerOne.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isNull();
        Instant hospitalTwoFirstCutoff = offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt();
        assertThat(hospitalTwoFirstCutoff).isNotNull();
        Instant hospitalThreeFirstCutoff = offerRepository.findById(offerThree.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt();
        assertThat(hospitalThreeFirstCutoff).isNotNull();

        var changed = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-2", offerTwo.getPublicId()
        );
        assertThat(changed.resultType()).isEqualTo(TransportDestinationResultType.CHANGED);
        assertThat(changed.previousDestinationOfferId()).isEqualTo(offerOne.getPublicId());
        assertThat(offerRepository.findById(offerOne.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isNotNull();
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isNull();
        assertThat(offerRepository.findById(offerThree.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isEqualTo(hospitalThreeFirstCutoff);

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
        assertThat(latestEffectiveDestinations).hasSize(1);
        var latestDestination = latestEffectiveDestinations.getFirst();
        assertThat(latestDestination.getTransportRequestId()).isEqualTo(transportRequestId);
        assertThat(latestDestination.getDestinationOfferId()).isEqualTo(offerTwo.getId());
        assertThat(latestDestination.getOccurredAt())
                .isCloseTo(changed.changedAt(), within(1, ChronoUnit.MICROS));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));

        var changedBack = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "destination-key-4", offerOne.getPublicId()
        );
        assertThat(changedBack.resultType()).isEqualTo(TransportDestinationResultType.CHANGED);
        assertThat(changedBack.previousDestinationOfferId()).isEqualTo(offerTwo.getPublicId());
        assertThat(offerRepository.findById(offerOne.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isNull();
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isNotNull()
                .isAfterOrEqualTo(hospitalTwoFirstCutoff);
        var changedBackDestinations = commandRepository.findLatestEffectiveDestinations(
                Set.of(transportRequestId)
        );
        assertThat(changedBackDestinations).hasSize(1);
        var changedBackDestination = changedBackDestinations.getFirst();
        assertThat(changedBackDestination.getDestinationOfferId()).isEqualTo(offerOne.getId());
        assertThat(changedBackDestination.getOccurredAt())
                .isCloseTo(changedBack.changedAt(), within(1, ChronoUnit.MICROS));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));
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
    void pendingHospitalRemainsActiveAndCanAcceptAfterAnotherDestinationIsSelected() throws Exception {
        UserAccount paramedic = createParamedic("activependingmedic");
        UserAccount destinationHospital = createHospital("activependinghospital1", "37.6021000");
        UserAccount pendingHospital = createHospital("activependinghospital2", "37.6121000");
        UserAccount rejectingHospital = createHospital("activependinghospital3", "37.6221000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var destinationOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(destinationHospital.getId()))
                .findFirst().orElseThrow();
        var pendingOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(pendingHospital.getId()))
                .findFirst().orElseThrow();
        var rejectingOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(rejectingHospital.getId()))
                .findFirst().orElseThrow();

        offerService.accept(
                hospitalPrincipal(destinationHospital),
                destinationOffer.getPublicId(),
                "active-pending-destination-accept"
        );
        destinationService.select(
                paramedicPrincipal(paramedic),
                requestId,
                "active-pending-destination-select",
                destinationOffer.getPublicId()
        );

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("AWAITING_RESPONSE"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital))
                        .header("Idempotency-Key", "active-pending-late-accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.transportRequestStatus").value("EN_ROUTE"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/reject", rejectingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectingHospital))
                        .header("Idempotency-Key", "active-pending-late-reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPECIALIST_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.transportRequestStatus").value("EN_ROUTE"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("REJECTED"));
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
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false))
                .andExpect(jsonPath("$.items[0].canWithdraw").value(true))
                .andExpect(jsonPath("$.items[0].ageStatus").isNotEmpty())
                .andExpect(jsonPath("$.items[0].routeEstimateStatus").doesNotExist());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.currentDestination").value(false))
                .andExpect(jsonPath("$.route.status").doesNotExist());

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
                .andExpect(jsonPath("$.items[0].reRequested").value(false))
                .andExpect(jsonPath("$.items[0].lastRequestedAt").isNotEmpty())
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
        UserAccount pendingHospital = createHospital("currentwithdrawhospital3", "37.6221000");
        UserAccount rejectingHospital = createHospital("currentwithdrawhospital4", "37.6321000");
        String requestId = createAndSearch(paramedic);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();
        var pendingOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(pendingHospital.getId()))
                .findFirst().orElseThrow();
        var rejectedOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(rejectingHospital.getId()))
                .findFirst().orElseThrow();
        offerService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "accept-current-withdraw-1");
        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "accept-current-withdraw-2");
        offerService.reject(
                hospitalPrincipal(rejectingHospital),
                rejectedOffer.getPublicId(),
                "reject-current-withdraw-4",
                new RejectHospitalOfferRequest(
                        HospitalRejectionReason.SPECIALIST_UNAVAILABLE,
                        null
                )
        );
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "select-current-withdraw", offerOne.getPublicId()
        );
        Instant acceptedOfferCutoff = offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt();
        Instant originalPendingOfferedAt = pendingOffer.getOfferedAt();
        UserAccount newlyEligibleHospital = createHospital(
                "currentwithdrawhospital5", "37.6421000"
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
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isEqualTo(acceptedOfferCutoff);

        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        assertThat(recovery.getAttemptNumber()).isEqualTo(2);
        assertThat(recovery.getTriggerType()).isEqualTo(HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL);
        assertThat(recovery.getSearchOriginLatitude()).isEqualByComparingTo(stored.getOriginLatitude());
        assertThat(recovery.getSearchOriginLongitude()).isEqualByComparingTo(stored.getOriginLongitude());

        var storedPending = offerRepository.findById(pendingOffer.getId()).orElseThrow();
        assertThat(storedPending.getPublicId()).isEqualTo(pendingOffer.getPublicId());
        assertThat(storedPending.getStatus()).isEqualTo(HospitalOfferStatus.PENDING);
        assertThat(storedPending.getOfferedAt()).isEqualTo(originalPendingOfferedAt);
        assertThat(storedPending.isReRequested()).isTrue();
        assertThat(storedPending.getRenotificationCount()).isEqualTo(1);
        assertThat(storedPending.getLastRequestedAt()).isAfterOrEqualTo(originalPendingOfferedAt);
        assertThat(storedPending.getLastRequestedAttempt().getId()).isEqualTo(recovery.getId());
        assertThat(storedPending.getClinicalVisibilityCutoffAt()).isNotNull();
        assertThat(offerRepository.findById(rejectedOffer.getId()).orElseThrow().isReRequested()).isFalse();
        assertThat(offerEventRepository.findAll().stream()
                .filter(event -> event.getHospitalOffer().getId().equals(pendingOffer.getId()))
                .filter(event -> event.getEventType() == HospitalOfferEventType.RENOTIFIED))
                .hasSize(1);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.TRANSPORT_REQUEST_RECEIVED)
                .filter(event -> event.getAudiencePublicId().equals(
                        pendingHospital.getOrganization().getPublicId()
                ))
                .filter(event -> event.getAggregatePublicId().equals(pendingOffer.getPublicId())))
                .hasSize(2);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN)
                .filter(event -> event.getAudiencePublicId().equals(hospitalTwo.getOrganization().getPublicId()))
                .filter(event -> event.getAggregateType().equals("TRANSPORT_REQUEST"))
                .filter(event -> event.getAggregatePublicId().equals(requestId)))
                .hasSize(1);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getAudiencePublicId().equals(
                        rejectingHospital.getOrganization().getPublicId()
                ))
                .filter(event -> !event.getOccurredAt().isBefore(recovery.getStartedAt())))
                .isEmpty();

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDestinationOfferId").doesNotExist())
                .andExpect(jsonPath("$.currentAttempt.triggerType").value("ACCEPTANCE_WITHDRAWAL"));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerId").value(pendingOffer.getPublicId()))
                .andExpect(jsonPath("$.items[0].reRequested").value(true))
                .andExpect(jsonPath("$.items[0].lastRequestedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing.reRequested").value(true))
                .andExpect(jsonPath("$.timing.lastRequestedAt").isNotEmpty());

        long attemptCountBeforeReplay = attemptRepository.findAll().stream()
                .filter(attempt -> attempt.getTransportRequest().getPublicId().equals(requestId))
                .count();
        long cardCountBeforeReplay = offerRepository
                .findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId)
                .size();
        long renotifiedEventsBeforeReplay = offerEventRepository.findAll().stream()
                .filter(event -> event.getHospitalOffer().getId().equals(pendingOffer.getId()))
                .filter(event -> event.getEventType() == HospitalOfferEventType.RENOTIFIED)
                .count();
        long pendingSignalsBeforeReplay = outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.TRANSPORT_REQUEST_RECEIVED)
                .filter(event -> event.getAudiencePublicId().equals(
                        pendingHospital.getOrganization().getPublicId()
                ))
                .filter(event -> event.getAggregatePublicId().equals(pendingOffer.getPublicId()))
                .count();
        long acceptedSignalsBeforeReplay = outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN)
                .filter(event -> event.getAudiencePublicId().equals(
                        hospitalTwo.getOrganization().getPublicId()
                ))
                .filter(event -> event.getAggregatePublicId().equals(requestId))
                .count();

        offerService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "withdraw-current-key",
                new WithdrawHospitalAcceptanceRequest(
                        HospitalAcceptanceWithdrawalReason.SPECIALIST_UNAVAILABLE,
                        null
                )
        );
        assertThat(offerRepository.findById(pendingOffer.getId()).orElseThrow().getRenotificationCount())
                .isEqualTo(1);
        assertThat(offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId)).hasSize(4);
        assertThat(offerEventRepository.findAll().stream()
                .filter(event -> event.getHospitalOffer().getId().equals(pendingOffer.getId()))
                .filter(event -> event.getEventType() == HospitalOfferEventType.RENOTIFIED))
                .hasSize(1);
        assertThat(attemptRepository.findAll().stream()
                .filter(attempt -> attempt.getTransportRequest().getPublicId().equals(requestId)))
                .hasSize((int) attemptCountBeforeReplay);
        assertThat(offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId))
                .hasSize((int) cardCountBeforeReplay);
        assertThat(renotifiedEventsBeforeReplay).isEqualTo(1);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.TRANSPORT_REQUEST_RECEIVED)
                .filter(event -> event.getAudiencePublicId().equals(
                        pendingHospital.getOrganization().getPublicId()
                ))
                .filter(event -> event.getAggregatePublicId().equals(pendingOffer.getPublicId())))
                .hasSize((int) pendingSignalsBeforeReplay);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getEventType() == RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN)
                .filter(event -> event.getAudiencePublicId().equals(
                        hospitalTwo.getOrganization().getPublicId()
                ))
                .filter(event -> event.getAggregatePublicId().equals(requestId)))
                .hasSize((int) acceptedSignalsBeforeReplay);
        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne))
                        .header("Idempotency-Key", "withdraw-current-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BED_SHORTAGE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));
        assertThat(offerRepository.findById(pendingOffer.getId()).orElseThrow().getRenotificationCount())
                .isEqualTo(1);

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
    void withdrawnHospitalContactRemainsHiddenWhileRecoverySearchWaits() throws Exception {
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
                .isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.SEARCHING);
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].status").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.offers[0].hospitalContact").doesNotExist());
    }

    @Test
    void lateAcceptanceWhileWithdrawalRecoveryWaitsRestoresSelectableState() {
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
                .isEqualTo(TransportRequestStatus.SEARCHING);

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
    void lateAcceptanceStopsActiveWithdrawalRecoverySearch() {
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
                .isEqualTo(TransportRequestStatus.SEARCHING);
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.SEARCHING);

        offerService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "late-retry-accept-two");

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
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
