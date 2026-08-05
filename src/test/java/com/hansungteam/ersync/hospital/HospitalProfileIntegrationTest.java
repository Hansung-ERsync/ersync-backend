package com.hansungteam.ersync.hospital;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private JwtTokenService jwtTokenService;

    @Test
    void authenticatedHospitalReadsOnlyItsOwnProfileWithoutSensitiveCredentials() throws Exception {
        HospitalContext context = createHospital("profilehospital", "프로필 조회 병원");
        long auditCountBefore = auditEventRepository.count();

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.account())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(context.account().getPublicId()))
                .andExpect(jsonPath("$.loginId").value("profilehospital"))
                .andExpect(jsonPath("$.role").value("HOSPITAL_STAFF"))
                .andExpect(jsonPath("$.organizationId").value(context.organization().getPublicId()))
                .andExpect(jsonPath("$.organizationName").value("프로필 조회 병원"))
                .andExpect(jsonPath("$.hospitalId").value(context.profile().getPublicId()))
                .andExpect(jsonPath("$.address").value("서울특별시 성북구"))
                .andExpect(jsonPath("$.latitude").value(37.5821))
                .andExpect(jsonPath("$.longitude").value(127.0105))
                .andExpect(jsonPath("$.contact").value("02-1234-5678"))
                .andExpect(jsonPath("$.receivingStatus").value("OFF"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.invitationCode").doesNotExist());

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.account())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalId").value(context.profile().getPublicId()))
                .andExpect(jsonPath("$.receivingStatus").value("OFF"));

        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void profileReflectsReceivingStatusChangedByExistingPutApi() throws Exception {
        HospitalContext context = createHospital("profilestatus", "상태 연계 병원");
        String bearer = bearer(context.account());

        mockMvc.perform(put("/api/v1/hospitals/me/receiving-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON"));

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivingStatus").value("ON"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void authenticationAndHospitalRoleAreRequired() throws Exception {
        mockMvc.perform(get("/api/v1/hospitals/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        Organization ems = organizationRepository.save(Organization.create(
                "프로필 차단 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount paramedic = userAccountRepository.save(UserAccount.createMember(
                ems,
                "blockedmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "blockedadmin",
                "encoded-password"
        ));
        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void inactiveAccountAndMissingProfileAreRejected() throws Exception {
        HospitalContext inactive = createHospital("inactivehospital", "비활성 병원");
        String inactiveBearer = bearer(inactive.account());
        inactive.account().deactivate();
        userAccountRepository.saveAndFlush(inactive.account());

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, inactiveBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        Organization hospital = organizationRepository.save(Organization.create(
                "프로필 누락 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount missingProfile = userAccountRepository.save(UserAccount.createMember(
                hospital,
                "missinghospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingProfile)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_001"));
    }

    @Test
    void mismatchedProfileOrganizationIsDenied() throws Exception {
        Organization accountOrganization = organizationRepository.save(Organization.create(
                "인증 조직 병원",
                OrganizationType.HOSPITAL
        ));
        Organization profileOrganization = organizationRepository.save(Organization.create(
                "잘못 연결된 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                accountOrganization,
                "mismatchhospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        hospitalProfileRepository.save(HospitalProfile.create(
                profileOrganization,
                account,
                "서울특별시 종로구",
                new BigDecimal("37.5700000"),
                new BigDecimal("126.9800000"),
                "02-9999-9999"
        ));

        mockMvc.perform(get("/api/v1/hospitals/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    private HospitalContext createHospital(String loginId, String organizationName) {
        Organization organization = organizationRepository.save(Organization.create(
                organizationName,
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = hospitalProfileRepository.save(HospitalProfile.create(
                organization,
                account,
                "서울특별시 성북구",
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                "02-1234-5678"
        ));
        return new HospitalContext(organization, account, profile);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }

    private record HospitalContext(
            Organization organization,
            UserAccount account,
            HospitalProfile profile
    ) {
    }
}
