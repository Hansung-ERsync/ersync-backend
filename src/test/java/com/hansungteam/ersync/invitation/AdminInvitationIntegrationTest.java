package com.hansungteam.ersync.invitation;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private SecretDigester secretDigester;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserAccount admin;
    private Organization hospital;

    @BeforeEach
    void setUp() {
        admin = userAccountRepository.save(UserAccount.createSuperAdmin("admin1", "encoded-password"));
        hospital = organizationRepository.save(Organization.create("한성대학교병원", OrganizationType.HOSPITAL));
    }

    @Test
    void issuesListsAndRevokesCodeWithoutPersistingPlainText() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/invitation-codes")
                        .with(authentication(authenticationFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "role": "HOSPITAL_STAFF",
                                  "expiryOption": "THREE_DAYS"
                                }
                                """.formatted(hospital.getPublicId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.invitation.status").value("AVAILABLE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String plainText = json.get("code").asText();
        var saved = invitationCodeRepository.findAll().getFirst();

        assertThat(plainText).matches("[A-Za-z0-9_-]{8}");
        assertThat(Arrays.equals(saved.getCodeDigest(), secretDigester.digest(plainText))).isTrue();
        assertThat(new String(saved.getCodeDigest(), StandardCharsets.UTF_8)).isNotEqualTo(plainText);
        assertThat(auditEventRepository.countByAction(AuditAction.INVITATION_ISSUED)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/invitation-codes")
                        .with(authentication(authenticationFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invitationCodeId").value(saved.getPublicId()))
                .andExpect(jsonPath("$.items[0].code").doesNotExist())
                .andExpect(jsonPath("$.items[0].codeDigest").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/invitation-codes/{id}/revoke", saved.getPublicId())
                        .with(authentication(authenticationFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        assertThat(auditEventRepository.countByAction(AuditAction.INVITATION_REVOKED)).isEqualTo(1);
    }

    @Test
    void rejectsOrganizationAndRoleMismatch() throws Exception {
        mockMvc.perform(post("/api/v1/admin/invitation-codes")
                        .with(authentication(authenticationFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "role": "PARAMEDIC",
                                  "expiryOption": "SEVEN_DAYS"
                                }
                                """.formatted(hospital.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void expiresDueCodeOnceAndRecordsAudit() {
        var generated = secretDigester.generate();
        InvitationCode invitation = invitationCodeRepository.save(InvitationCode.issue(
                hospital,
                com.hansungteam.ersync.global.security.UserRole.HOSPITAL_STAFF,
                generated.digest(),
                Instant.now().minusSeconds(1),
                admin
        ));

        assertThat(invitationService.expireDueCodes()).isEqualTo(1);
        assertThat(invitationService.expireDueCodes()).isZero();
        assertThat(invitation.getStatus())
                .isEqualTo(com.hansungteam.ersync.invitation.domain.InvitationStatus.EXPIRED);
        assertThat(auditEventRepository.countByAction(AuditAction.INVITATION_EXPIRED)).isEqualTo(1);
    }

    private UsernamePasswordAuthenticationToken authenticationFor(UserAccount account) {
        AuthenticatedAccount principal = new AuthenticatedAccount(
                account.getPublicId(),
                null,
                account.getRole()
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.authorities()
        );
    }
}
