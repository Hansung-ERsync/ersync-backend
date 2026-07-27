package com.hansungteam.ersync.auth;

import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ersync.bootstrap.super-admin.login-id=admin",
        "ersync.bootstrap.super-admin.password=admin-password",
        "ersync.security.jwt.secret=test-jwt-secret-which-is-long-enough"
})
class AccountOrganizationInvitationSecurityIntegrationTest {

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");
    private static final Pattern ORGANIZATION_ID_PATTERN = Pattern.compile("\"organizationId\":\"([^\"]+)\"");
    private static final Pattern PLAINTEXT_CODE_PATTERN = Pattern.compile("\"plaintextCode\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Test
    void adminCreatesOrganizationAndInvitationThenUserSignsUpAndLogsIn() throws Exception {
        String adminToken = login("admin", "admin-password");
        String organizationId = createOrganization(adminToken, "EMS Alpha");
        String invitationCode = issueInvitationCode(adminToken, organizationId);

        mockMvc.perform(post("/api/v1/auth/invitation-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s"
                                }
                                """.formatted(invitationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.organizationType").value("EMS_UNIT"))
                .andExpect(jsonPath("$.targetRole").value("PARAMEDIC"));

        mockMvc.perform(get("/api/v1/admin/invitation-codes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].plaintextCode").doesNotExist())
                .andExpect(content().string(not(containsString(invitationCode))));

        assertThat(invitationCodeRepository.findAll().getFirst().codeHash()).doesNotContain(invitationCode);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "loginId": "paramedic.one",
                                  "password": "paramedic-password"
                                }
                                """.formatted(invitationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.loginId").value("paramedic.one"));

        assertThat(invitationCodeRepository.findAll().getFirst().status())
                .isEqualTo(InvitationCodeStatus.USED);

        String paramedicToken = login("paramedic.one", "paramedic-password");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedicToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));
    }

    @Test
    void invalidInvitationCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "missing-code",
                                  "loginId": "paramedic.two",
                                  "password": "paramedic-password"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_001"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    @WithMockUser(roles = "PARAMEDIC")
    void organizationAdminApiRequiresSuperAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "EMS_UNIT",
                                  "name": "Forbidden EMS"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void invitationRoleMustMatchOrganizationType() throws Exception {
        String adminToken = login("admin", "admin-password");
        String organizationId = createOrganization(adminToken, "Hospital Beta");

        mockMvc.perform(post("/api/v1/admin/organizations/{organizationId}/invitation-codes", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "PARAMEDIC",
                                  "expiresInDays": 3
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_002"));
    }

    private String login(String loginId, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "%s"
                                }
                                """.formatted(loginId, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(ACCESS_TOKEN_PATTERN, response);
    }

    private String createOrganization(String adminToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "%s",
                                  "name": "%s"
                                }
                                """.formatted(name.startsWith("Hospital") ? "HOSPITAL" : "EMS_UNIT", name)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(ORGANIZATION_ID_PATTERN, response);
    }

    private String issueInvitationCode(String adminToken, String organizationId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/organizations/{organizationId}/invitation-codes", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "PARAMEDIC",
                                  "expiresInDays": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(PLAINTEXT_CODE_PATTERN, response);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String extract(Pattern pattern, String response) {
        Matcher matcher = pattern.matcher(response);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
