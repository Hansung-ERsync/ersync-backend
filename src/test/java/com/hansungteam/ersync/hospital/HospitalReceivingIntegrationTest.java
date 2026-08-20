package com.hansungteam.ersync.hospital;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalReceivingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private HospitalProfileRepository hospitalProfileRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void hospitalChangesOnlyItsOwnReceivingStatusAndAuditIsRecorded() throws Exception {
        Organization hospital = organizationRepository.save(Organization.create(
                "수신상태병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                hospital,
                "hospitalon",
                passwordEncoder.encode("safe-password"),
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = hospitalProfileRepository.save(HospitalProfile.create(
                hospital,
                account,
                "서울특별시 성북구",
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                "02-1234-5678"
        ));
        String accessToken = login("hospitalon", UserRole.HOSPITAL_STAFF);

        mockMvc.perform(put("/api/v1/hospitals/me/receiving-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalId").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.status").value("ON"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(hospitalProfileRepository.findById(profile.getId()).orElseThrow().getReceivingStatus())
                .isEqualTo(ReceivingStatus.ON);
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_RECEIVING_STATUS_CHANGED))
                .isEqualTo(1);
    }

    @Test
    void paramedicCannotChangeHospitalReceivingStatus() throws Exception {
        Organization ems = organizationRepository.save(Organization.create(
                "수신상태구급대",
                OrganizationType.EMS_UNIT
        ));
        userAccountRepository.save(UserAccount.createMember(
                ems,
                "medicstatus",
                passwordEncoder.encode("safe-password"),
                UserRole.PARAMEDIC
        ));
        String accessToken = login("medicstatus", UserRole.PARAMEDIC);

        mockMvc.perform(put("/api/v1/hospitals/me/receiving-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private String login(String loginId, UserRole role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "safe-password",
                                  "role": "%s"
                                }
                                """.formatted(loginId, role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
