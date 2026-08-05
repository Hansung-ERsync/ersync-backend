package com.hansungteam.ersync.mvp;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVP의 앱 가입부터 최초 병원 요청까지 끊김 없이 연결되는 대표 사용자 여정입니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MvpJourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private InvitationService invitationService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private JwtTokenService jwtTokenService;

    @Test
    void paramedicSignsUpAndCreatesOnePatientRequestThatAutomaticallyReachesHospitals() throws Exception {
        UserAccount admin = accountRepository.save(UserAccount.createSuperAdmin(
                "mvpjourneyadmin", "encoded-password"
        ));
        Organization emsUnit = organizationRepository.save(Organization.create(
                "MVP 종합 구급대", OrganizationType.EMS_UNIT
        ));
        String invitationCode = invitationService.issue(
                admin.getPublicId(),
                new IssueInvitationRequest(
                        emsUnit.getPublicId(),
                        UserRole.PARAMEDIC,
                        InvitationExpiryOption.THREE_DAYS,
                        null
                )
        ).code();
        UserAccount hospitalOne = createReceivingHospital("mvpjourneyhospital1", "37.6021000");
        createReceivingHospital("mvpjourneyhospital2", "37.6121000");
        createReceivingHospital("mvpjourneyhospital3", "37.6221000");

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + invitationCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "displayName": "김현장",
                                  "loginId": "mvpjourneymedic",
                                  "password": "safe-password",
                                  "contact": "010-1111-2222",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(invitationCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.organizationId").value(emsUnit.getPublicId()));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "mvpjourneymedic",
                                  "password": "safe-password",
                                  "role": "PARAMEDIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginBody).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("김현장"))
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.callbackContact").value("010-1111-2222"));
        mockMvc.perform(get("/api/v1/assessment-protocols/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("ERSYNC_MVP_1.0"));

        String creationBody = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("Idempotency-Key", "mvp-journey-request-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.assessmentProtocolVersion").value("ERSYNC_MVP_1.0"))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(creationBody).get("transportRequestId").asText();

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("Idempotency-Key", "mvp-journey-request-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestId").value(requestId));

        var attempt = attemptRepository
                .findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());

        assertThat(offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId))
                .hasSize(3);
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.currentAttempt.currentRadiusKm").value(10))
                .andExpect(jsonPath("$.offers.length()").value(3));
        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].transportRequestId").value(requestId))
                .andExpect(jsonPath("$.items[0].offerStatus").value("PENDING"));
    }

    private UserAccount createReceivingHospital(String loginId, String latitude) {
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

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
