package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssessmentProtocolIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void paramedicCanReadDevelopmentProtocolAndAdminCannot() throws Exception {
        Organization organization = organizationRepository.save(Organization.create(
                "프로토콜 테스트 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount paramedic = userAccountRepository.save(UserAccount.createMember(
                organization,
                "protocolmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "protocoladmin",
                "encoded-password"
        ));

        mockMvc.perform(get("/api/v1/assessment-protocols/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("ERSYNC_MVP_1.0"))
                .andExpect(jsonPath("$.status").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.preKtasStandardVersion").value("DEV_UNCONFIRMED"))
                .andExpect(jsonPath("$.enumValues.vitalSignType.length()").value(5))
                .andExpect(jsonPath("$.vitalSignUnits.BLOOD_PRESSURE").value("mmHg"));

        mockMvc.perform(get("/api/v1/assessment-protocols/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
