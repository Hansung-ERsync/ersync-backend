package com.hansungteam.ersync.auth;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "loginadmin",
                passwordEncoder.encode("safe-password")
        ));
    }

    @Test
    void loginAccessTokenAndRefreshRotationWorkEndToEnd() throws Exception {
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(admin.getPublicId()))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginBody);
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");

        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        String refreshBody = mockMvc.perform(post("/api/v1/auth/tokens/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replacementRefreshToken = objectMapper.readTree(refreshBody)
                .get("refreshToken")
                .asText();
        assertThat(replacementRefreshToken)
                .matches("[A-Za-z0-9_-]{43}")
                .isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/tokens/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void wrongCredentialsDoNotRevealWhichFieldFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "wrong-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "unknown1",
                                  "password": "wrong-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void inactiveAccountAndInvalidBearerTokenAreRejected() throws Exception {
        admin.deactivate();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_002"));

        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void tokenIsRejectedAfterAccountBecomesInactive() throws Exception {
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginBody).get("accessToken").asText();

        admin.deactivate();
        userAccountRepository.flush();

        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void sameLoginIdAcrossRolesAuthenticatesOnlyTheRequestedRoleAccount() throws Exception {
        Organization hospital = organizationRepository.save(Organization.create(
                "동일 아이디 인증 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount hospitalAccount = userAccountRepository.save(UserAccount.createMember(
                hospital,
                "loginadmin",
                passwordEncoder.encode("hospital-password"),
                UserRole.HOSPITAL_STAFF
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(admin.getPublicId()))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "hospital-password",
                                  "role": "HOSPITAL_STAFF"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(hospitalAccount.getPublicId()))
                .andExpect(jsonPath("$.organizationId").value(hospital.getPublicId()))
                .andExpect(jsonPath("$.role").value("HOSPITAL_STAFF"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "HOSPITAL_STAFF"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "hospital-password",
                                  "role": "PARAMEDIC"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void roleIsRequiredAndUnknownRoleUsesStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginadmin",
                                  "password": "safe-password",
                                  "role": "UNKNOWN_ROLE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
