package com.hansungteam.ersync.hospital;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class HospitalProfileReceivingStateIntegrationTest {

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");
    private static final Pattern ORGANIZATION_ID_PATTERN = Pattern.compile("\"organizationId\":\"([^\"]+)\"");
    private static final Pattern PLAINTEXT_CODE_PATTERN = Pattern.compile("\"plaintextCode\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hospitalStaffCreatesProfileThenTurnsReceivingStatusOn() throws Exception {
        String hospitalToken = createHospitalStaff("Hospital Gamma", "hospital.gamma");

        mockMvc.perform(put("/api/v1/hospital/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "erAddress": "Seoul Mapo Emergency Center",
                                  "latitude": 37.550000,
                                  "longitude": 126.910000,
                                  "erContact": "02-1234-5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Hospital Gamma"))
                .andExpect(jsonPath("$.receivingStatus").value("OFF"))
                .andExpect(jsonPath("$.locationVerifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").isNumber());

        mockMvc.perform(get("/api/v1/hospital/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erAddress").value("Seoul Mapo Emergency Center"))
                .andExpect(jsonPath("$.receivingStatus").value("OFF"));

        mockMvc.perform(put("/api/v1/hospital/receiving-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivingStatus").value("ON"));
    }

    @Test
    void receivingStatusChangeWithoutProfileIsRejected() throws Exception {
        String hospitalToken = createHospitalStaff("Hospital Delta", "hospital.delta");

        mockMvc.perform(put("/api/v1/hospital/receiving-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_001"));
    }

    @Test
    void invalidCoordinateIsRejected() throws Exception {
        String hospitalToken = createHospitalStaff("Hospital Epsilon", "hospital.epsilon");

        mockMvc.perform(put("/api/v1/hospital/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "erAddress": "Invalid Coordinate ER",
                                  "latitude": 91.000000,
                                  "longitude": 126.910000,
                                  "erContact": "02-1234-5678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @WithMockUser(roles = "PARAMEDIC")
    void paramedicCannotAccessHospitalProfileApi() throws Exception {
        mockMvc.perform(get("/api/v1/hospital/profile"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private String createHospitalStaff(String organizationName, String loginId) throws Exception {
        String adminToken = login("admin", "admin-password");
        String organizationId = createHospitalOrganization(adminToken, organizationName);
        String invitationCode = issueHospitalInvitationCode(adminToken, organizationId);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "loginId": "%s",
                                  "password": "hospital-password"
                                }
                                """.formatted(invitationCode, loginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.role").value("HOSPITAL_STAFF"));

        return login(loginId, "hospital-password");
    }

    private String createHospitalOrganization(String adminToken, String organizationName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "HOSPITAL",
                                  "name": "%s"
                                }
                                """.formatted(organizationName)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(ORGANIZATION_ID_PATTERN, response);
    }

    private String issueHospitalInvitationCode(String adminToken, String organizationId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/organizations/{organizationId}/invitation-codes", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "HOSPITAL_STAFF",
                                  "expiresInDays": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(PLAINTEXT_CODE_PATTERN, response);
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
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(ACCESS_TOKEN_PATTERN, response);
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
