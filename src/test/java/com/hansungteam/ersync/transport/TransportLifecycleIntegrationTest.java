package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.api.RejectHospitalOfferRequest;
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.CancelTransportRequestRequest;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportLifecycleCommandRepository;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransportLifecycleIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportLifecycleCommandRepository commandRepository;
    @Autowired private TransportDestinationCommandRepository destinationCommandRepository;
    @Autowired private RealtimeOutboxEventRepository outboxRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private TransportLifecycleService lifecycleService;
    @Autowired private HospitalReceivingService receivingService;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private MockMvc mockMvc;

    @Test
    void cancelClosesSearchAndOffersAndReplaysExactlyOnce() throws Exception {
        UserAccount paramedic = createParamedic("cancelmedic");
        UserAccount hospitalOne = createHospital("cancelhospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("cancelhospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "cancel-request-key");

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "cancel-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"  현장 처치로 해결  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reason").value("OTHER"))
                .andExpect(jsonPath("$.detail").value("현장 처치로 해결"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "cancel-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"현장 처치로 해결\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "cancel-command-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCENE_RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));

        var stored = requestRepository.findByPublicId(requestId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TransportRequestStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).isEqualTo(TransportCancellationReason.OTHER);
        assertThat(stored.getCurrentDestinationOffer()).isNull();
        assertThat(attemptRepository.findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow().getStatus()).isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_CANCELLATION);
        assertThat(offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId))
                .allMatch(offer -> offer.getStatus() == HospitalOfferStatus.PENDING)
                .allMatch(offer -> offer.getClosedAt() != null);
        assertThat(commandRepository.countByTransportRequestId(stored.getId())).isEqualTo(1);
        assertThat(auditRepository.countByAction(AuditAction.TRANSPORT_CANCELLED)).isEqualTo(1);
        assertThat(outboxRepository.countByEventType(RealtimeEventType.TRANSPORT_CANCELLED)).isEqualTo(3);

        assertHospitalMovedToMinimalHistory(hospitalOne);
        assertHospitalMovedToMinimalHistory(hospitalTwo);

        mockMvc.perform(get("/api/v1/transport-requests")
                        .queryParam("view", "RECENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].hospitalName").doesNotExist())
                .andExpect(jsonPath("$.items[0].cancellationReason").value("OTHER"));
    }

    @Test
    void cancellationValidatesReasonDetailOwnerAndTerminalState() throws Exception {
        UserAccount owner = createParamedic("cancelvalidationowner");
        UserAccount other = createParamedic("cancelvalidationother");
        String requestId = requestService.create(
                paramedicPrincipal(owner),
                "cancel-validation-request",
                ValidTransportRequestFixtures.request()
        ).response().transportRequestId();

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "cancel-validation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "cancel-validation-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCENE_RESOLVED\",\"detail\":\"보내면 안 됨\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .header("Idempotency-Key", "cancel-validation-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCENE_RESOLVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"));

        lifecycleService.cancel(
                paramedicPrincipal(owner),
                requestId,
                "cancel-validation-success",
                new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
        );
        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "cancel-validation-new-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCENE_RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_004"));
    }

    @Test
    void searchingWithoutCandidatesAndAcceptedAvailableCanBothBeCancelled() {
        UserAccount searchingOwner = createParamedic("searchingcancelmedic");
        String searchingRequestId = requestService.create(
                paramedicPrincipal(searchingOwner),
                "searching-cancel-request",
                ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        var searchingAttempt = attemptRepository
                .findByTransportRequestPublicIdAndAttemptNumber(searchingRequestId, 1)
                .orElseThrow();
        searchService.processDueAttempt(searchingAttempt.getId());
        assertThat(requestRepository.findByPublicId(searchingRequestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.SEARCHING);

        lifecycleService.cancel(
                paramedicPrincipal(searchingOwner),
                searchingRequestId,
                "searching-cancel-command",
                new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
        );
        assertThat(requestRepository.findByPublicId(searchingRequestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANCELLED);

        UserAccount acceptedOwner = createParamedic("acceptedcancelmedic");
        UserAccount hospital = createHospital("acceptedcancelhospital", "37.6021000");
        String acceptedRequestId = createAndSearch(acceptedOwner, "accepted-cancel-request");
        HospitalOffer acceptedOffer = offerFor(hospital, acceptedRequestId);
        offerService.accept(
                hospitalPrincipal(hospital), acceptedOffer.getPublicId(), "accepted-cancel-accept"
        );
        assertThat(requestRepository.findByPublicId(acceptedRequestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);

        lifecycleService.cancel(
                paramedicPrincipal(acceptedOwner),
                acceptedRequestId,
                "accepted-cancel-command",
                new CancelTransportRequestRequest(
                        TransportCancellationReason.GUARDIAN_SELF_TRANSPORT, null
                )
        );
        assertThat(requestRepository.findByPublicId(acceptedRequestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANCELLED);
        assertThat(offerRepository.findById(acceptedOffer.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalOfferStatus.ACCEPTED);
        assertThat(offerRepository.findById(acceptedOffer.getId()).orElseThrow().getClosedAt())
                .isNotNull();
    }

    @Test
    void cancellationPreservesRejectedOutcomeAndCancelsAcceptedAndPendingHospitals() throws Exception {
        UserAccount paramedic = createParamedic("mixedcancelmedic");
        UserAccount acceptedHospital = createHospital("mixedcancelaccepted", "37.6021000");
        UserAccount rejectedHospital = createHospital("mixedcancelrejected", "37.6121000");
        UserAccount pendingHospital = createHospital("mixedcancelpending", "37.6221000");
        String requestId = createAndSearch(paramedic, "mixed-cancel-request");
        HospitalOffer acceptedOffer = offerFor(acceptedHospital, requestId);
        HospitalOffer rejectedOffer = offerFor(rejectedHospital, requestId);

        offerService.accept(
                hospitalPrincipal(acceptedHospital),
                acceptedOffer.getPublicId(),
                "mixed-cancel-accept"
        );
        offerService.reject(
                hospitalPrincipal(rejectedHospital),
                rejectedOffer.getPublicId(),
                "mixed-cancel-reject",
                new RejectHospitalOfferRequest(HospitalRejectionReason.SPECIALIST_UNAVAILABLE, null)
        );
        lifecycleService.cancel(
                paramedicPrincipal(paramedic),
                requestId,
                "mixed-cancel-command",
                new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
        );

        Instant cancelledAt = requestRepository.findByPublicId(requestId).orElseThrow().getCancelledAt();
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(acceptedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("TRANSPORT_CANCELLED"))
                .andExpect(jsonPath("$.items[0].processedAt").value(cancelledAt.toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("TRANSPORT_CANCELLED"))
                .andExpect(jsonPath("$.items[0].processedAt").value(cancelledAt.toString()));
    }

    @Test
    void handoffNeedsParamedicRequestAndDestinationHospitalConfirmation() throws Exception {
        UserAccount paramedic = createParamedic("handoffmedic");
        UserAccount hospital = createHospital("handoffhospital", "37.6021000");
        UserAccount otherAcceptedHospital = createHospital("handoffotheraccepted", "37.6121000");
        UserAccount rejectedHospital = createHospital("handoffrejected", "37.6221000");
        UserAccount withdrawnHospital = createHospital("handoffwithdrawn", "37.6321000");
        UserAccount pendingHospital = createHospital("handoffpending", "37.6421000");
        String requestId = createAndSearch(paramedic, "handoff-request-key");
        HospitalOffer offer = offerFor(hospital, requestId);
        HospitalOffer otherAcceptedOffer = offerFor(otherAcceptedHospital, requestId);
        HospitalOffer rejectedOffer = offerFor(rejectedHospital, requestId);
        HospitalOffer withdrawnOffer = offerFor(withdrawnHospital, requestId);
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "handoff-accept-key");
        offerService.accept(
                hospitalPrincipal(otherAcceptedHospital),
                otherAcceptedOffer.getPublicId(),
                "handoff-other-accept-key"
        );
        offerService.accept(
                hospitalPrincipal(withdrawnHospital),
                withdrawnOffer.getPublicId(),
                "handoff-withdrawn-accept-key"
        );
        offerService.reject(
                hospitalPrincipal(rejectedHospital),
                rejectedOffer.getPublicId(),
                "handoff-reject-key",
                new RejectHospitalOfferRequest(HospitalRejectionReason.ER_GENERAL_BED_SHORTAGE, null)
        );
        var destinationSelected = destinationService.select(
                paramedicPrincipal(paramedic), requestId, "handoff-destination-key", offer.getPublicId()
        );
        offerService.withdrawAcceptance(
                hospitalPrincipal(withdrawnHospital),
                withdrawnOffer.getPublicId(),
                "handoff-withdrawn-command-key",
                new WithdrawHospitalAcceptanceRequest(
                        HospitalAcceptanceWithdrawalReason.BED_SHORTAGE,
                        null
                )
        );

        Long transportRequestId = requestRepository.findByPublicId(requestId).orElseThrow().getId();
        var latestDestinations = destinationCommandRepository.findLatestEffectiveDestinations(
                Set.of(transportRequestId)
        );
        assertThat(latestDestinations).hasSize(1);
        var latestDestination = latestDestinations.getFirst();
        assertThat(latestDestination.getDestinationOfferId()).isEqualTo(offer.getId());
        assertThat(latestDestination.getOccurredAt())
                .isCloseTo(destinationSelected.changedAt(), within(1, ChronoUnit.MICROS));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAcceptedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("EN_ROUTE"))
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(withdrawnHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.items[0].processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("AWAITING_RESPONSE"))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/handoff-request", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "handoff-command-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HANDOFF_REQUESTED"))
                .andExpect(jsonPath("$.destinationOfferId").value(offer.getPublicId()))
                .andExpect(jsonPath("$.destinationHospitalName").value(hospital.getOrganization().getName()))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(get("/api/v1/transport-requests")
                        .queryParam("view", "RECENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("HANDOFF_REQUESTED"))
                .andExpect(jsonPath("$.items[0].hospitalName").value(hospital.getOrganization().getName()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].canConfirmHandoff").value(true))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("HANDOFF_REQUESTED"));

        receivingService.change(hospitalPrincipal(hospital), ReceivingStatus.OFF);
        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/confirm-handoff", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "handoff-command-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));
        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/confirm-handoff", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "handoff-confirm-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));
        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/confirm-handoff", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "handoff-confirm-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        var completed = requestRepository.findByPublicId(requestId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TransportRequestStatus.COMPLETED);
        assertThat(completed.getCurrentDestinationOffer()).isNotNull();
        assertThat(completed.getHandoffRequestedAt()).isNotNull();
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getClosedAt()).isNotNull();
        assertThat(commandRepository.countByTransportRequestId(completed.getId())).isEqualTo(2);
        assertThat(auditRepository.countByAction(AuditAction.HANDOFF_REQUESTED)).isEqualTo(1);
        assertThat(auditRepository.countByAction(AuditAction.HANDOFF_CONFIRMED)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("HANDOFF_COMPLETED_HERE"))
                .andExpect(jsonPath("$.items[0].processedAt").value(completed.getCompletedAt().toString()))
                .andExpect(jsonPath("$.items[0].canConfirmHandoff").value(false))
                .andExpect(jsonPath("$.items[0].completedAt").exists())
                .andExpect(jsonPath("$.items[0].ageStatus").doesNotExist());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAcceptedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("COMPLETED_ELSEWHERE"))
                .andExpect(jsonPath("$.items[0].processedAt").value(completed.getCompletedAt().toString()))
                .andExpect(jsonPath("$.items[0].currentDestination").value(false));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectedHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(withdrawnHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("ACCEPTANCE_WITHDRAWN"))
                .andExpect(jsonPath("$.items[0].processedAt").exists());
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("COMPLETED_ELSEWHERE"))
                .andExpect(jsonPath("$.items[0].processedAt").value(completed.getCompletedAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/cancel", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "after-complete-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCENE_RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_004"));
    }

    @Test
    void nonDestinationHospitalCannotConfirmAndHandoffBlocksWithdrawal() throws Exception {
        UserAccount paramedic = createParamedic("handoffscopemedic");
        UserAccount destinationHospital = createHospital("handoffscopehospital1", "37.6021000");
        UserAccount otherHospital = createHospital("handoffscopehospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "handoff-scope-request");
        HospitalOffer destination = offerFor(destinationHospital, requestId);
        HospitalOffer other = offerFor(otherHospital, requestId);
        offerService.accept(hospitalPrincipal(destinationHospital), destination.getPublicId(), "scope-accept-1");
        offerService.accept(hospitalPrincipal(otherHospital), other.getPublicId(), "scope-accept-2");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "scope-select", destination.getPublicId()
        );
        lifecycleService.requestHandoff(
                paramedicPrincipal(paramedic), requestId, "scope-handoff-request"
        );

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/confirm-handoff", other.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherHospital))
                        .header("Idempotency-Key", "scope-wrong-confirm"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance", destination.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(destinationHospital))
                        .header("Idempotency-Key", "scope-after-handoff-withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BED_SHORTAGE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_004"));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/handoff-request", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "scope-handoff-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/handoff-request", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "scope-handoff-new-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_004"));
    }

    @Test
    void cancelledDestinationNameIsHistoryOnlyAndOtherParamedicCannotListIt() throws Exception {
        UserAccount owner = createParamedic("cancelhistoryowner");
        UserAccount otherParamedic = createParamedic("cancelhistoryother");
        UserAccount hospital = createHospital("cancelhistoryhospital", "37.6021000");
        String requestId = createAndSearch(owner, "cancel-history-request");
        HospitalOffer offer = offerFor(hospital, requestId);
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "cancel-history-accept");
        destinationService.select(
                paramedicPrincipal(owner), requestId, "cancel-history-select", offer.getPublicId()
        );
        lifecycleService.cancel(
                paramedicPrincipal(owner),
                requestId,
                "cancel-history-command",
                new CancelTransportRequestRequest(TransportCancellationReason.PATIENT_REFUSED_TRANSPORT, null)
        );

        mockMvc.perform(get("/api/v1/transport-requests")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hospitalName").value(hospital.getOrganization().getName()))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/transport-requests")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherParamedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private void assertHospitalMovedToMinimalHistory(UserAccount hospital) throws Exception {
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .queryParam("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].transportRequestStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("TRANSPORT_CANCELLED"))
                .andExpect(jsonPath("$.items[0].processedAt").exists())
                .andExpect(jsonPath("$.items[0].ageStatus").doesNotExist());
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

    private HospitalOffer offerFor(UserAccount hospital, String requestId) {
        return offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospital.getId()))
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

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
