package com.hansungteam.ersync.account;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.account.api.HospitalSignupRequest;
import com.hansungteam.ersync.account.api.ParamedicSignupRequest;
import com.hansungteam.ersync.account.application.AccountSignupService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private AccountSignupService accountSignupService;

    @Autowired
    private SecretDigester secretDigester;

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

        UserAccount account = userAccountRepository.findByLoginIdAndRole(
                "hansung1",
                UserRole.HOSPITAL_STAFF
        ).orElseThrow();
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
                                  "displayName": "  김민준  ",
                                  "loginId": "medic01",
                                  "password": "safe-password",
                                  "contact": "010-1234-5678",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.organizationId").value(emsUnit.getPublicId()))
                .andExpect(jsonPath("$.hospitalId").doesNotExist());

        UserAccount account = userAccountRepository.findByLoginIdAndRole(
                "medic01",
                UserRole.PARAMEDIC
        ).orElseThrow();
        var profile = paramedicProfileRepository.findByAccountPublicId(account.getPublicId()).orElseThrow();
        assertThat(account.getOrganization().getPublicId()).isEqualTo(emsUnit.getPublicId());
        assertThat(account.getRole()).isEqualTo(UserRole.PARAMEDIC);
        assertThat(profile.getDisplayName()).isEqualTo("김민준");
        assertThat(profile.getContact()).isEqualTo("010-1234-5678");
        assertThat(contactSharingConsentRepository.existsByAccountPublicIdAndConsentTypeAndPolicyVersion(
                account.getPublicId(),
                ConsentType.CONTACT_COLLECTION_USE,
                "COLLECTION_USE_DEV_1.0"
        )).isTrue();
        assertThat(contactSharingConsentRepository.existsByAccountPublicIdAndConsentTypeAndPolicyVersion(
                account.getPublicId(),
                ConsentType.HOSPITAL_PROVISION,
                "HOSPITAL_PROVISION_DEV_1.0"
        )).isTrue();
    }

    @Test
    void legacyLongInvitationStillCreatesParamedicAccount() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "기존 코드 호환 구급대",
                OrganizationType.EMS_UNIT
        ));
        String legacyCode = secretDigester.generate().plainText();
        assertThat(legacyCode).matches("[A-Za-z0-9_-]{43}");
        InvitationCode invitation = invitationCodeRepository.save(InvitationCode.issue(
                emsUnit,
                UserRole.PARAMEDIC,
                secretDigester.digest(legacyCode),
                Instant.now().plusSeconds(3600),
                admin
        ));

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "displayName": "기존 코드 대원",
                                  "loginId": "legacymedic",
                                  "password": "safe-password",
                                  "contact": "010-9876-5432",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(legacyCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARAMEDIC"))
                .andExpect(jsonPath("$.organizationId").value(emsUnit.getPublicId()));

        assertThat(userAccountRepository.existsByLoginIdAndRole("legacymedic", UserRole.PARAMEDIC)).isTrue();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
    }

    @Test
    void paramedicAndHospitalStaffCanShareOneLoginId() {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "동일 아이디 구급대",
                OrganizationType.EMS_UNIT
        ));
        Organization hospital = organizationRepository.save(Organization.create(
                "동일 아이디 병원",
                OrganizationType.HOSPITAL
        ));
        String paramedicCode = issue(emsUnit, UserRole.PARAMEDIC);
        String hospitalCode = issue(hospital, UserRole.HOSPITAL_STAFF);

        accountSignupService.signupParamedic(new ParamedicSignupRequest(
                paramedicCode,
                "동일 아이디 대원",
                "sharedmember",
                "paramedic-password",
                "010-1234-5678",
                true,
                "COLLECTION_USE_DEV_1.0",
                true,
                "HOSPITAL_PROVISION_DEV_1.0"
        ));
        accountSignupService.signupHospital(new HospitalSignupRequest(
                hospitalCode,
                hospital.getName(),
                "sharedmember",
                "hospital-password",
                "서울특별시 성북구",
                new java.math.BigDecimal("37.5821000"),
                new java.math.BigDecimal("127.0105000"),
                "02-1234-5678",
                true,
                "CONTACT_SHARING_DEV_1.0"
        ));

        assertThat(userAccountRepository.findByLoginIdAndRole("sharedmember", UserRole.PARAMEDIC))
                .get()
                .extracting(UserAccount::getOrganization)
                .isEqualTo(emsUnit);
        assertThat(userAccountRepository.findByLoginIdAndRole("sharedmember", UserRole.HOSPITAL_STAFF))
                .get()
                .extracting(UserAccount::getOrganization)
                .isEqualTo(hospital);
        assertThat(userAccountRepository.count()).isEqualTo(3L);
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
                                  "displayName": "잘못된 아이디",
                                  "loginId": "Medic01",
                                  "password": "safe-password",
                                  "contact": "010-1234-5678",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(invitationCodeRepository.findAll().getFirst().getStatus())
                .isEqualTo(InvitationStatus.AVAILABLE);
    }

    @Test
    void bothParamedicConsentsAreRequiredBeforeInvitationIsConsumed() throws Exception {
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
                                  "displayName": "동의 실패",
                                  "loginId": "medic02",
                                  "password": "safe-password",
                                  "contact": "010-9999-8888",
                                  "collectionUseConsentAccepted": false,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(userAccountRepository.existsByLoginIdAndRole("medic02", UserRole.PARAMEDIC))
                .isFalse();
        assertThat(paramedicProfileRepository.count()).isZero();
        assertThat(contactSharingConsentRepository.count()).isZero();
        assertThat(invitationCodeRepository.findAll().getFirst().getStatus())
                .isEqualTo(InvitationStatus.AVAILABLE);
    }

    @Test
    void invalidDisplayNameDoesNotConsumeInvitation() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "이름 검증 구급대",
                OrganizationType.EMS_UNIT
        ));
        String code = issue(emsUnit, UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "displayName": " 한 ",
                                  "loginId": "medic03",
                                  "password": "safe-password",
                                  "contact": "010-1111-2222",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(userAccountRepository.existsByLoginIdAndRole("medic03", UserRole.PARAMEDIC))
                .isFalse();
        assertThat(invitationCodeRepository.findAll().getFirst().getStatus())
                .isEqualTo(InvitationStatus.AVAILABLE);
    }

    @Test
    void twoStepSignupLoginAndProfileRestoreWorkAsOneFlow() throws Exception {
        Organization emsUnit = organizationRepository.save(Organization.create(
                "통합 흐름 구급대",
                OrganizationType.EMS_UNIT
        ));
        String code = issue(emsUnit, UserRole.PARAMEDIC);

        mockMvc.perform(post("/api/v1/auth/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.role").value("PARAMEDIC"));

        mockMvc.perform(post("/api/v1/auth/signups/paramedic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationCode": "%s",
                                  "displayName": "통합 대원",
                                  "loginId": "flowmedic",
                                  "password": "safe-password",
                                  "contact": "010-2222-3333",
                                  "collectionUseConsentAccepted": true,
                                  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
                                  "hospitalProvisionConsentAccepted": true,
                                  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "flowmedic",
                                  "password": "safe-password",
                                  "role": "PARAMEDIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/paramedics/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("flowmedic"))
                .andExpect(jsonPath("$.displayName").value("통합 대원"))
                .andExpect(jsonPath("$.organizationName").value(emsUnit.getName()))
                .andExpect(jsonPath("$.callbackContact").value("010-2222-3333"))
                .andExpect(jsonPath("$.privacyConsent.legacyCombined").value(false));
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
