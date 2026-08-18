package com.hansungteam.ersync.paramedic;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileQueryService;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileCommandService;
import com.hansungteam.ersync.paramedic.api.UpdateParamedicProfileRequest;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParamedicProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private ParamedicProfileQueryService profileQueryService;
    @Autowired private ParamedicProfileCommandService profileCommandService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void authenticatedParamedicReadsOwnProfileAndCurrentConsents() throws Exception {
        UserAccount account = createParamedic("profilemedic", "김민준", false);

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getPublicId()))
                .andExpect(jsonPath("$.loginId").value("profilemedic"))
                .andExpect(jsonPath("$.displayName").value("김민준"))
                .andExpect(jsonPath("$.organizationId").value(account.getOrganization().getPublicId()))
                .andExpect(jsonPath("$.organizationName").value(account.getOrganization().getName()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.callbackContact").value("010-0000-0001"))
                .andExpect(jsonPath("$.privacyConsent.collectionUsePolicyVersion")
                        .value("COLLECTION_USE_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.hospitalProvisionPolicyVersion")
                        .value("HOSPITAL_PROVISION_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.consentedAt").isNotEmpty())
                .andExpect(jsonPath("$.privacyConsent.legacyCombined").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.invitationCode").doesNotExist());
    }

    @Test
    void legacyCombinedConsentAndLoginIdDisplayNameRemainReadable() throws Exception {
        UserAccount account = createParamedic("legacyprofile", null, true);

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("legacyprofile"))
                .andExpect(jsonPath("$.privacyConsent.collectionUsePolicyVersion")
                        .value("CONTACT_SHARING_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.hospitalProvisionPolicyVersion")
                        .value("CONTACT_SHARING_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.legacyCombined").value(true));
    }

    @Test
    void paramedicUpdatesOwnDisplayNameAndCallbackContactWithoutChangingConsent() throws Exception {
        UserAccount account = createParamedic("updatemedic", "기존 대원", false);
        String bearer = bearer(account);
        long auditCountBefore = auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED);
        String payload = """
                {
                  "displayName": "  김민준 대원  ",
                  "callbackContact": "  010-9999-8888  "
                }
                """;

        mockMvc.perform(put("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getPublicId()))
                .andExpect(jsonPath("$.loginId").value("updatemedic"))
                .andExpect(jsonPath("$.displayName").value("김민준 대원"))
                .andExpect(jsonPath("$.organizationId").value(account.getOrganization().getPublicId()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.callbackContact").value("010-9999-8888"))
                .andExpect(jsonPath("$.privacyConsent.collectionUsePolicyVersion")
                        .value("COLLECTION_USE_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.hospitalProvisionPolicyVersion")
                        .value("HOSPITAL_PROVISION_DEV_1.0"))
                .andExpect(jsonPath("$.privacyConsent.legacyCombined").value(false));

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("김민준 대원"))
                .andExpect(jsonPath("$.callbackContact").value("010-9999-8888"));

        mockMvc.perform(put("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("김민준 대원"))
                .andExpect(jsonPath("$.callbackContact").value("010-9999-8888"));

        assertThat(auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED))
                .isEqualTo(auditCountBefore + 2);
        assertThat(paramedicProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidParamedicUpdateDoesNotChangeEitherFieldOrCreateAudit() throws Exception {
        UserAccount account = createParamedic("invalidupdatemedic", "기존 이름", false);
        String bearer = bearer(account);
        long auditCountBefore = auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED);
        List<UpdateParamedicProfileRequest> invalidRequests = List.of(
                new UpdateParamedicProfileRequest(" ", "010-7777-6666"),
                new UpdateParamedicProfileRequest("김", "010-7777-6666"),
                new UpdateParamedicProfileRequest("가".repeat(51), "010-7777-6666"),
                new UpdateParamedicProfileRequest("잘못된\n이름", "010-7777-6666"),
                new UpdateParamedicProfileRequest("정상 대원", "invalid contact")
        );

        for (UpdateParamedicProfileRequest invalidRequest : invalidRequests) {
            mockMvc.perform(put("/api/v1/paramedics/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("기존 이름"))
                .andExpect(jsonPath("$.callbackContact").value("010-0000-0001"));
        assertThat(auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED))
                .isEqualTo(auditCountBefore);
    }

    @Test
    void authenticationAndParamedicRoleAreRequired() throws Exception {
        mockMvc.perform(get("/api/v1/paramedics/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
        mockMvc.perform(put("/api/v1/paramedics/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        Organization hospital = organizationRepository.save(Organization.create(
                "프로필 차단 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount hospitalAccount = userAccountRepository.save(UserAccount.createMember(
                hospital,
                "profilehospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalAccount)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(put("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "profileadmin",
                "encoded-password"
        ));
        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void inactiveAccountAndMismatchedOrganizationAreRejected() throws Exception {
        UserAccount account = createParamedic("inactiveprofile", "비활성 대원", false);
        account.deactivate();
        userAccountRepository.saveAndFlush(account);

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        UserAccount active = createParamedic("mismatchprofile", "불일치 대원", false);
        assertThatThrownBy(() -> profileQueryService.getMine(new AuthenticatedAccount(
                active.getPublicId(),
                "00000000-0000-0000-0000-000000000000",
                UserRole.PARAMEDIC
        )))
                .hasMessageContaining("접근 권한");
        assertThatThrownBy(() -> profileCommandService.update(
                new AuthenticatedAccount(
                        active.getPublicId(),
                        "00000000-0000-0000-0000-000000000000",
                        UserRole.PARAMEDIC
                ),
                new UpdateParamedicProfileRequest("수정 대원", "010-1111-2222")
        ))
                .hasMessageContaining("접근 권한");
    }

    @Test
    void missingProfileOrRequiredConsentsUseStandardErrors() throws Exception {
        Organization organization = organizationRepository.save(Organization.create(
                "누락 검증 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount missingProfile = userAccountRepository.save(UserAccount.createMember(
                organization,
                "missingprofile",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingProfile)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_001"));
        mockMvc.perform(put("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingProfile))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_001"));

        UserAccount missingConsent = userAccountRepository.save(UserAccount.createMember(
                organization,
                "missingconsent",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                missingConsent,
                organization,
                "동의 누락",
                "010-0000-0002"
        ));
        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingConsent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_005"));

        long auditCountBefore = auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED);
        mockMvc.perform(put("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingConsent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_005"));
        ParamedicProfile unchanged = paramedicProfileRepository
                .findByAccountPublicId(missingConsent.getPublicId())
                .orElseThrow();
        assertThat(unchanged.getDisplayName()).isEqualTo("동의 누락");
        assertThat(unchanged.getContact()).isEqualTo("010-0000-0002");
        assertThat(auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED))
                .isEqualTo(auditCountBefore);
    }

    private UserAccount createParamedic(String loginId, String displayName, boolean legacyConsent) {
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
        if (displayName == null) {
            paramedicProfileRepository.save(ParamedicProfile.create(
                    account,
                    organization,
                    "010-0000-0001"
            ));
        } else {
            paramedicProfileRepository.save(ParamedicProfile.create(
                    account,
                    organization,
                    displayName,
                    "010-0000-0001"
            ));
        }

        Instant consentedAt = Instant.parse("2026-08-04T09:00:00Z");
        if (legacyConsent) {
            consentRepository.save(ContactSharingConsent.record(
                    account,
                    "CONTACT_SHARING_DEV_1.0",
                    consentedAt
            ));
        } else {
            consentRepository.save(ContactSharingConsent.record(
                    account,
                    ConsentType.CONTACT_COLLECTION_USE,
                    "COLLECTION_USE_DEV_1.0",
                    consentedAt
            ));
            consentRepository.save(ContactSharingConsent.record(
                    account,
                    ConsentType.HOSPITAL_PROVISION,
                    "HOSPITAL_PROVISION_DEV_1.0",
                    consentedAt
            ));
        }
        return account;
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }

    private String validUpdateRequest() {
        return """
                {
                  "displayName": "수정 대원",
                  "callbackContact": "010-1111-2222"
                }
                """;
    }
}
