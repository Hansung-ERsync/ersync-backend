package com.hansungteam.ersync.mvp;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
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
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.api.CancelTransportRequestRequest;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.TransportClinicalUpdateService;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가입부터 이송 종료까지 상태 경합과 복구 분기를 실제 서비스·API 계약으로 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MvpCollisionJourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportCurrentLocationRepository locationRepository;
    @Autowired private InvitationService invitationService;
    @Autowired private TransportRequestService requestService;
    @Autowired private HospitalSearchService searchService;
    @Autowired private HospitalOfferService offerService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private TransportLocationService locationService;
    @Autowired private TransportClinicalUpdateService clinicalUpdateService;
    @Autowired private TransportLifecycleService lifecycleService;
    @Autowired private JwtTokenService jwtTokenService;

    @Test
    void sharedLoginIdRolesCompleteTheNormalJourneyWithoutCrossingAuthority() throws Exception {
        UserAccount paramedic = onboardParamedic("sharedjourney", "paramedic-password");
        UserAccount destinationHospital = createHospital(
                "sharedjourney", "hospital-password", "37.6021000"
        );
        createHospital("normalhospital2", "hospital-password", "37.6121000");
        createHospital("normalhospital3", "hospital-password", "37.6221000");

        String paramedicToken = login("sharedjourney", "paramedic-password", UserRole.PARAMEDIC);
        String hospitalToken = login("sharedjourney", "hospital-password", UserRole.HOSPITAL_STAFF);
        assertInvalidLogin("sharedjourney", "paramedic-password", UserRole.HOSPITAL_STAFF);
        assertInvalidLogin("sharedjourney", "hospital-password", UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken)
                        .header("Idempotency-Key", "normal-wrong-role-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        String createdBody = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-create-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(createdBody).get("transportRequestId").asText();

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-create-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestId").value(requestId));

        processInitialSearch(requestId);
        HospitalOffer destination = offerFor(destinationHospital, requestId);

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", destination.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-wrong-role-accept"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", destination.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken)
                        .header("Idempotency-Key", "normal-accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTED"));
        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", destination.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken)
                        .header("Idempotency-Key", "normal-accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/destination", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":\"" + destination.getPublicId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestStatus").value("EN_ROUTE"));

        Instant capturedAt = Instant.now().minusSeconds(5);
        var location = new UpdateTransportLocationRequest(
                new BigDecimal("37.5800000"), new BigDecimal("127.0300000"), capturedAt
        );
        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationReplaced").value(true));

        var vitalSigns = laterVitalSigns(60);
        mockMvc.perform(post(
                                "/api/v1/transport-requests/{requestId}/clinical-updates/vital-signs",
                                requestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-vital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vitalSigns)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(
                                "/api/v1/transport-requests/{requestId}/clinical-updates/vital-signs",
                                requestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-vital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vitalSigns)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", destination.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.58));
        mockMvc.perform(get(
                                "/api/v1/hospitals/me/offers/{offerId}/clinical-timeline",
                                destination.getPublicId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/handoff-request", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + paramedicToken)
                        .header("Idempotency-Key", "normal-handoff-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HANDOFF_REQUESTED"));
        mockMvc.perform(post(
                                "/api/v1/hospitals/me/offers/{offerId}/confirm-handoff",
                                destination.getPublicId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken)
                        .header("Idempotency-Key", "normal-handoff-confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post(
                                "/api/v1/hospitals/me/offers/{offerId}/confirm-handoff",
                                destination.getPublicId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hospitalToken)
                        .header("Idempotency-Key", "normal-handoff-confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertRecentStatus(paramedic, "COMPLETED");
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.COMPLETED);
    }

    @Test
    void destinationWithdrawalUsesLatestLocationAndFallsBackToAnotherAcceptance() throws Exception {
        UserAccount paramedic = createParamedic("fallbackmedic");
        UserAccount firstHospital = createHospital("fallbackhospital1", "safe-password", "37.6021000");
        UserAccount secondHospital = createHospital("fallbackhospital2", "safe-password", "37.6121000");
        String requestId = createAndSearch(paramedic, "fallback-request");
        HospitalOffer firstOffer = offerFor(firstHospital, requestId);
        HospitalOffer secondOffer = offerFor(secondHospital, requestId);
        offerService.accept(hospitalPrincipal(firstHospital), firstOffer.getPublicId(), "fallback-accept-1");
        offerService.accept(hospitalPrincipal(secondHospital), secondOffer.getPublicId(), "fallback-accept-2");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "fallback-select-1", firstOffer.getPublicId()
        );

        locationService.update(
                paramedicPrincipal(paramedic),
                requestId,
                "fallback-latest-location",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7000000"),
                        new BigDecimal("127.2000000"),
                        Instant.now().minusSeconds(5)
                )
        );
        createHospital("fallbackhospital3", "safe-password", "37.7020000");

        var withdrawal = offerService.withdrawAcceptance(
                hospitalPrincipal(firstHospital),
                firstOffer.getPublicId(),
                "fallback-withdraw-1",
                new WithdrawHospitalAcceptanceRequest(
                        HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null
                )
        );
        assertThat(withdrawal.transportRequestStatus()).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
        assertThat(withdrawal.searchRestarted()).isTrue();
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getCurrentDestinationOffer())
                .isNull();

        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        assertThat(recovery.getAttemptNumber()).isEqualTo(2);
        assertThat(recovery.getSearchOriginLatitude()).isEqualByComparingTo("37.7000000");
        assertThat(recovery.getSearchOriginLongitude()).isEqualByComparingTo("127.2000000");
        searchService.processDueAttempt(recovery.getId());
        assertThat(offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(recovery.getId()))
                .anyMatch(offer -> offer.getHospitalNameSnapshot().equals("fallbackhospital3 병원"));

        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "fallback-select-2", secondOffer.getPublicId()
        );
        assertThat(attemptRepository.findById(recovery.getId()).orElseThrow().getStatus())
                .isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_DESTINATION);
        lifecycleService.requestHandoff(
                paramedicPrincipal(paramedic), requestId, "fallback-handoff-request"
        );
        lifecycleService.confirmHandoff(
                hospitalPrincipal(secondHospital), secondOffer.getPublicId(), "fallback-handoff-confirm"
        );

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.COMPLETED);
        assertRecentStatus(paramedic, "COMPLETED");
    }

    @Test
    void simultaneousHandoffRequestAndDestinationWithdrawalCompletesThroughTheWinningBranch()
            throws Exception {
        UserAccount paramedic = createParamedic("handoffbranchmedic");
        UserAccount firstHospital = createHospital(
                "handoffbranchhospital1", "safe-password", "37.6021000"
        );
        UserAccount secondHospital = createHospital(
                "handoffbranchhospital2", "safe-password", "37.6121000"
        );
        String requestId = createAndSearch(paramedic, "handoff-branch-request");
        HospitalOffer firstOffer = offerFor(firstHospital, requestId);
        HospitalOffer secondOffer = offerFor(secondHospital, requestId);
        offerService.accept(hospitalPrincipal(firstHospital), firstOffer.getPublicId(), "branch-accept-1");
        offerService.accept(hospitalPrincipal(secondHospital), secondOffer.getPublicId(), "branch-accept-2");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "branch-select-1", firstOffer.getPublicId()
        );

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.requestHandoff(
                        paramedicPrincipal(paramedic), requestId, "branch-handoff-request"
                )),
                () -> capture(() -> offerService.withdrawAcceptance(
                        hospitalPrincipal(firstHospital),
                        firstOffer.getPublicId(),
                        "branch-withdraw",
                        new WithdrawHospitalAcceptanceRequest(
                                HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null
                        )
                ))
        );

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode))
                .containsExactly("TRANSPORT_004");
        TransportRequestStatus status = requestRepository.findByPublicId(requestId).orElseThrow().getStatus();
        if (status == TransportRequestStatus.HANDOFF_REQUESTED) {
            lifecycleService.confirmHandoff(
                    hospitalPrincipal(firstHospital), firstOffer.getPublicId(), "branch-confirm-1"
            );
        } else {
            assertThat(status).isEqualTo(TransportRequestStatus.ACCEPTED_AVAILABLE);
            destinationService.select(
                    paramedicPrincipal(paramedic), requestId, "branch-select-2", secondOffer.getPublicId()
            );
            lifecycleService.requestHandoff(
                    paramedicPrincipal(paramedic), requestId, "branch-handoff-request-2"
            );
            lifecycleService.confirmHandoff(
                    hospitalPrincipal(secondHospital), secondOffer.getPublicId(), "branch-confirm-2"
            );
        }

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.COMPLETED);
        assertRecentStatus(paramedic, "COMPLETED");
    }

    @Test
    void simultaneousCancellationAndAcceptanceEndsCancelledAndCannotResume() throws Exception {
        UserAccount paramedic = createParamedic("cancelbranchmedic");
        UserAccount hospital = createHospital("cancelbranchhospital", "safe-password", "37.6021000");
        String requestId = createAndSearch(paramedic, "cancel-branch-request");
        HospitalOffer offer = offerFor(hospital, requestId);

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.cancel(
                        paramedicPrincipal(paramedic),
                        requestId,
                        "cancel-branch-command",
                        new CancelTransportRequestRequest(
                                TransportCancellationReason.PATIENT_REFUSED_TRANSPORT, null
                        )
                )),
                () -> capture(() -> offerService.accept(
                        hospitalPrincipal(hospital), offer.getPublicId(), "cancel-branch-accept"
                ))
        );

        assertThat(outcomes).anyMatch(Outcome::successful);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode))
                .allMatch("TRANSPORT_006"::equals);
        var replay = lifecycleService.cancel(
                paramedicPrincipal(paramedic),
                requestId,
                "cancel-branch-command",
                new CancelTransportRequestRequest(
                        TransportCancellationReason.PATIENT_REFUSED_TRANSPORT, null
                )
        );
        assertThat(replay.idempotentReplay()).isTrue();

        var stored = requestRepository.findByPublicId(requestId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TransportRequestStatus.CANCELLED);
        assertThat(stored.getCurrentDestinationOffer()).isNull();
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getClosedAt()).isNotNull();
        assertThatThrownBy(() -> destinationService.select(
                paramedicPrincipal(paramedic), requestId, "cancel-branch-select", offer.getPublicId()
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode().getCode()).isEqualTo("TRANSPORT_004"));
        assertRecentStatus(paramedic, "CANCELLED");
    }

    @Test
    void simultaneousHandoffConfirmationAndLocationUpdateNeverWritesAfterCompletion()
            throws Exception {
        UserAccount paramedic = createParamedic("completebranchmedic");
        UserAccount hospital = createHospital("completebranchhospital", "safe-password", "37.6021000");
        String requestId = createAndSearch(paramedic, "complete-branch-request");
        HospitalOffer offer = offerFor(hospital, requestId);
        offerService.accept(hospitalPrincipal(hospital), offer.getPublicId(), "complete-branch-accept");
        destinationService.select(
                paramedicPrincipal(paramedic), requestId, "complete-branch-select", offer.getPublicId()
        );
        locationService.update(
                paramedicPrincipal(paramedic),
                requestId,
                "complete-location-before",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.6000000"),
                        new BigDecimal("127.1000000"),
                        Instant.now().minusSeconds(10)
                )
        );
        clinicalUpdateService.addVitalSigns(
                paramedicPrincipal(paramedic), requestId, "complete-vital-before", laterVitalSigns(60)
        );
        lifecycleService.requestHandoff(
                paramedicPrincipal(paramedic), requestId, "complete-handoff-request"
        );

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> lifecycleService.confirmHandoff(
                        hospitalPrincipal(hospital), offer.getPublicId(), "complete-handoff-confirm"
                )),
                () -> capture(() -> locationService.update(
                        paramedicPrincipal(paramedic),
                        requestId,
                        "complete-location-race",
                        new UpdateTransportLocationRequest(
                                new BigDecimal("37.6100000"),
                                new BigDecimal("127.1100000"),
                                Instant.now().minusSeconds(5)
                        )
                ))
        );

        assertThat(outcomes.getFirst().successful()).isTrue();
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode))
                .allMatch("TRANSPORT_004"::equals);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.COMPLETED);
        assertThat(locationRepository.findByTransportRequestPublicId(requestId)).isPresent();

        assertThatThrownBy(() -> clinicalUpdateService.addVitalSigns(
                paramedicPrincipal(paramedic), requestId, "complete-vital-after", laterVitalSigns(120)
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode().getCode()).isEqualTo("TRANSPORT_004"));
        assertThatThrownBy(() -> lifecycleService.cancel(
                paramedicPrincipal(paramedic),
                requestId,
                "complete-cancel-after",
                new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode().getCode()).isEqualTo("TRANSPORT_004"));
        assertRecentStatus(paramedic, "COMPLETED");
    }

    private UserAccount onboardParamedic(String loginId, String password) throws Exception {
        UserAccount admin = accountRepository.save(UserAccount.createSuperAdmin(
                "collisionadmin", passwordEncoder.encode("admin-password")
        ));
        Organization ems = organizationRepository.save(Organization.create(
                "충돌 시나리오 구급대", OrganizationType.EMS_UNIT
        ));
        String invitationCode = invitationService.issue(
                admin.getPublicId(),
                new IssueInvitationRequest(
                        ems.getPublicId(),
                        UserRole.PARAMEDIC,
                        InvitationExpiryOption.THREE_DAYS,
                        null
                )
        ).code();

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + invitationCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));
        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "displayName": "충돌검증 대원",
                                  "loginId": "%s",
                                  "password": "%s",
                                  "contact": "010-0000-1000",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(invitationCode, loginId, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));
        return accountRepository.findByLoginIdAndRole(loginId, UserRole.PARAMEDIC).orElseThrow();
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                passwordEncoder.encode("safe-password"),
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                account, organization, "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.now().minusSeconds(60)
        ));
        return account;
    }

    private UserAccount createHospital(
            String loginId,
            String password,
            String latitude
    ) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 병원", OrganizationType.HOSPITAL
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                passwordEncoder.encode(password),
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
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private String login(String loginId, String password, UserRole role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "%s",
                                  "role": "%s"
                                }
                                """.formatted(loginId, password, role.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void assertInvalidLogin(String loginId, String password, UserRole role) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "%s",
                                  "role": "%s"
                                }
                                """.formatted(loginId, password, role.name())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    private String createAndSearch(UserAccount paramedic, String idempotencyKey) {
        String requestId = requestService.create(
                paramedicPrincipal(paramedic), idempotencyKey, ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        processInitialSearch(requestId);
        return requestId;
    }

    private void processInitialSearch(String requestId) {
        var attempt = attemptRepository
                .findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        searchService.processDueAttempt(attempt.getId());
    }

    private HospitalOffer offerFor(UserAccount hospital, String requestId) {
        String hospitalName = hospital.getLoginId() + " 병원";
        return offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(offer -> offer.getHospitalNameSnapshot().equals(hospitalName))
                .findFirst()
                .orElseThrow();
    }

    private UpdateVitalSignsRequest laterVitalSigns(long seconds) {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        return new UpdateVitalSignsRequest(
                initial.measuredAt().plusSeconds(seconds),
                initial.enteredAt().plusSeconds(seconds),
                initial.measurements().stream()
                        .map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                                measurement.type(),
                                measurement.state(),
                                measurement.primaryValue(),
                                measurement.secondaryValue(),
                                measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        ))
                        .toList()
        );
    }

    private void assertRecentStatus(UserAccount paramedic, String expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/transport-requests")
                        .queryParam("view", "RECENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value(expectedStatus));
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
                throw new IllegalStateException("Concurrent scenario did not start");
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

    private record Outcome(boolean successful, String errorCode) {
    }
}
