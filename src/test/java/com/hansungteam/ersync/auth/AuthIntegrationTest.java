package com.hansungteam.ersync.auth;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
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
                                  "password": "safe-password"
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
        org.assertj.core.api.Assertions.assertThat(replacementRefreshToken).isNotEqualTo(refreshToken);

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
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "unknown1",
                                  "password": "wrong-password"
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
                                  "password": "safe-password"
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
                                  "password": "safe-password"
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
}
