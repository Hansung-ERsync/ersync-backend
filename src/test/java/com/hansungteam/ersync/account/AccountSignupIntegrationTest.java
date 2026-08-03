package com.hansungteam.ersync.account;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Autowired
    private HospitalProfileRepository hospitalProfileRepository;

    @Autowired
    private ParamedicProfileRepository paramedicProfileRepository;

    @Autowired
    private ContactSharingConsentRepository contactSharingConsentRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = userAccountRepository.save(UserAccount.createSuperAdmin("admin2", "encoded-password"));
    }

    @Test
    void hospitalSignupCreatesSharedAccountProfileAndConsumesCode() throws Exception {
        Organization hospital = organizationRepository.save(Organization.create(
                "서울한성병원",
                OrganizationType.HOSPITAL
        ));
        String code = issue(hospital, UserRole.HOSPITAL_STAFF);

        mockMvc.perform(post("/api/v1/auth/signups/hospital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "organizationName": "서울한성병원",
                                  "loginId": "hansung1",
                                  "password": "safe-password",
                                  "address": "서울특별시 성북구 삼선교로 16길",
                                  "latitude": 37.5821000,
                                  "longitude": 127.0105000,
                                  "contact": "02-1234-5678",
                                  "contactSharingConsentAccepted": true,
                                  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HOSPITAL_STAFF"))
                .andExpect(jsonPath("$.organizationId").value(hospital.getPublicId()))
                .andExpect(jsonPath("$.hospitalId").isNotEmpty())
                .andExpect(jsonPath("$.receivingStatus").value("OFF"));

        UserAccount account = userAccountRepository.findByLoginId("hansung1").orElseThrow();
        var profile = hospitalProfileRepository.findByAccountPublicId(account.getPublicId()).orElseThrow();
        var invitation = invitationCodeRepository.findAll().getFirst();

        assertThat(account.getRole()).isEqualTo(UserRole.HOSPITAL_STAFF);
        assertThat(passwordEncoder.matches("safe-password", account.getPasswordHash())).isTrue();
        assertThat(profile.getReceivingStatus()).isEqualTo(ReceivingStatus.OFF);
        assertThat(profile.getContact()).isEqualTo("02-1234-5678");
        assertThat(contactSharingConsentRepository.existsByAccountPublicIdAndPolicyVersion(
                account.getPublicId(),
                "CONTACT_SHARING_DEV_1.0"
        )).isTrue();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
        assertThat(auditEventRepository.countByAction(AuditAction.INVITATION_USED)).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.CONTACT_SHARING_CONSENT_RECORDED)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/signups/hospital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "organizationName": "서울한성병원",
                                  "loginId": "hansung2",
                                  "password": "safe-password",
                                  "address": "서울특별시 성북구 삼선교로 16길",
                                  "latitude": 37.5821000,
                                  "longitude": 127.0105000,
                                  "contact": "02-1234-5678",
                                  "contactSharingConsentAccepted": true,
                                  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_003"));
    }

    @Test
    void paramedicSignupCreatesIndividualAccountFromCodeOrganization() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "성북소방서 구급대",
                OrganizationType.EMS_UNIT
        ));
        String code = issue(emsUnit, UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "loginId": "medic01",
                                  "password": "safe-password",
                                  "contact": "010-1234-5678",
                                  "contactSharingConsentAccepted": true,
                                  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.organizationId").value(emsUnit.getPublicId()))
                .andExpect(jsonPath("$.hospitalId").doesNotExist());

        UserAccount account = userAccountRepository.findByLoginId("medic01").orElseThrow();
        var profile = paramedicProfileRepository.findByAccountPublicId(account.getPublicId()).orElseThrow();
        assertThat(account.getOrganization().getPublicId()).isEqualTo(emsUnit.getPublicId());
        assertThat(account.getRole()).isEqualTo(UserRole.PARAMEDIC);
        assertThat(profile.getContact()).isEqualTo("010-1234-5678");
        assertThat(contactSharingConsentRepository.existsByAccountPublicIdAndPolicyVersion(
                account.getPublicId(),
                "CONTACT_SHARING_DEV_1.0"
        )).isTrue();
    }

    @Test
    void invalidLoginIdIsRejectedBeforeCodeIsConsumed() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "강북소방서 구급대",
                OrganizationType.EMS_UNIT
        ));
        String code = issue(emsUnit, UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "loginId": "Medic01",
                                  "password": "safe-password",
                                  "contact": "010-1234-5678",
                                  "contactSharingConsentAccepted": true,
                                  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(invitationCodeRepository.findAll().getFirst().getStatus())
                .isEqualTo(InvitationStatus.AVAILABLE);
    }

    @Test
    void contactSharingConsentIsRequiredBeforeInvitationIsConsumed() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "도봉소방서 구급대",
                OrganizationType.EMS_UNIT
        ));
        String code = issue(emsUnit, UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "loginId": "medic02",
                                  "password": "safe-password",
                                  "contact": "010-9999-8888",
                                  "contactSharingConsentAccepted": false,
                                  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(userAccountRepository.existsByLoginId("medic02")).isFalse();
        assertThat(paramedicProfileRepository.count()).isZero();
        assertThat(contactSharingConsentRepository.count()).isZero();
        assertThat(invitationCodeRepository.findAll().getFirst().getStatus())
                .isEqualTo(InvitationStatus.AVAILABLE);
    }

    private String issue(Organization organization, UserRole role) {
        return invitationService.issue(
                        admin.getPublicId(),
                        new IssueInvitationRequest(
                                organization.getPublicId(),
                                role,
                                InvitationExpiryOption.THREE_DAYS,
                                null
                        )
                )
                .code();
    }
}
