package com.hansungteam.ersync.invitation;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvitationValidationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private InvitationCodeRepository invitationCodeRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private InvitationService invitationService;
    @Autowired private SecretDigester secretDigester;

    private UserAccount admin;
    private Organization emsUnit;

    @BeforeEach
    void setUp() {
        admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "validationadmin",
                "encoded-password"
        ));
        emsUnit = organizationRepository.save(Organization.create(
                "가입 확인 구급대",
                OrganizationType.EMS_UNIT
        ));
    }

    @Test
    void validCodeReturnsOrganizationAndConsentVersionsWithoutConsumingCode() throws Exception {
        String code = issueCode();

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("  " + code + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(emsUnit.getPublicId()))
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.requiredConsents[0].type").value("CONTACT_COLLECTION_USE"))
                .andExpect(jsonPath("$.requiredConsents[0].policyVersion").value("COLLECTION_USE_DEV_1.0"))
                .andExpect(jsonPath("$.requiredConsents[1].type").value("HOSPITAL_PROVISION"))
                .andExpect(jsonPath("$.requiredConsents[1].policyVersion")
                        .value("HOSPITAL_PROVISION_DEV_1.0"))
                .andExpect(jsonPath("$.invitationCode").doesNotExist())
                .andExpect(jsonPath("$.codeDigest").doesNotExist());

        InvitationCode stored = invitationCodeRepository.findAll().getFirst();
        assertThat(stored.getStatus()).isEqualTo(InvitationStatus.AVAILABLE);
        assertThat(stored.getUsedByAccount()).isNull();
        assertThat(auditEventRepository.countByAction(AuditAction.INVITATION_USED)).isZero();
    }

    @Test
    void caseChangedCodeIsInvalid() throws Exception {
        String code = "Ab12_-Z9";
        storeAvailableCode(code);

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(changeLetterCase(code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_001"));
    }

    @Test
    void legacyLongCodeRemainsValidWithoutBeingConsumed() throws Exception {
        String code = secretDigester.generate().plainText();
        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        storeAvailableCode(code);

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));

        InvitationCode stored = invitationCodeRepository.findAll().getFirst();
        assertThat(stored.getStatus()).isEqualTo(InvitationStatus.AVAILABLE);
        assertThat(stored.getUsedByAccount()).isNull();
    }

    @Test
    void usedExpiredAndRevokedCodesKeepExistingErrorContracts() throws Exception {
        String usedCode = issueCode();
        InvitationCode used = invitationCodeRepository.findAll().getFirst();
        UserAccount user = userAccountRepository.save(UserAccount.createMember(
                emsUnit,
                "usedvalidation",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        used.use(user, java.time.Instant.now());
        invitationCodeRepository.saveAndFlush(used);
        assertCodeError(usedCode, "INVITATION_003", 409);

        String expiredCode = issueCode();
        InvitationCode expired = invitationCodeRepository.findAll().stream()
                .filter(invitation -> invitation.getStatus() == InvitationStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();
        expired.expire();
        invitationCodeRepository.saveAndFlush(expired);
        assertCodeError(expiredCode, "INVITATION_002", 409);

        String revokedCode = issueCode();
        InvitationCode revoked = invitationCodeRepository.findAll().stream()
                .filter(invitation -> invitation.getStatus() == InvitationStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();
        revoked.revoke(java.time.Instant.now());
        invitationCodeRepository.saveAndFlush(revoked);
        assertCodeError(revokedCode, "INVITATION_004", 409);
    }

    private String issueCode() {
        String code = invitationService.issue(
                        admin.getPublicId(),
                        new IssueInvitationRequest(
                                emsUnit.getPublicId(),
                                UserRole.PARAMEDIC,
                                InvitationExpiryOption.THREE_DAYS,
                                null
                        )
                )
                .code();
        assertThat(code).matches("[A-Za-z0-9_-]{8}");
        return code;
    }

    private void storeAvailableCode(String code) {
        invitationCodeRepository.save(InvitationCode.issue(
                emsUnit,
                UserRole.PARAMEDIC,
                secretDigester.digest(code),
                Instant.now().plusSeconds(3600),
                admin
        ));
    }

    private void assertCodeError(String code, String errorCode, int httpStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(code)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private String request(String code) {
        return "{\"invitationCode\":\"" + code + "\"}";
    }

    private String changeLetterCase(String code) {
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if (Character.isLowerCase(character)) {
                return code.substring(0, index)
                        + Character.toUpperCase(character)
                        + code.substring(index + 1);
            }
            if (Character.isUpperCase(character)) {
                return code.substring(0, index)
                        + Character.toLowerCase(character)
                        + code.substring(index + 1);
            }
        }
        throw new IllegalStateException("Generated code must contain a letter");
    }
}
