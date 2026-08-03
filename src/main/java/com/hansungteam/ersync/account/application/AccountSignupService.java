package com.hansungteam.ersync.account.application;

import com.hansungteam.ersync.account.api.HospitalSignupRequest;
import com.hansungteam.ersync.account.api.ParamedicSignupRequest;
import com.hansungteam.ersync.account.api.SignupResponse;
import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.application.ContactPolicy;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** 가입 코드 소비와 계정·병원 프로필 생성을 하나의 트랜잭션으로 수행합니다. */
@Service
@RequiredArgsConstructor
public class AccountSignupService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final ParamedicProfileRepository paramedicProfileRepository;
    private final ContactSharingConsentRepository contactSharingConsentRepository;
    private final ContactSharingConsentPolicy contactSharingConsentPolicy;
    private final SecretDigester secretDigester;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    /** 병원 공용 계정과 수신 OFF 상태의 응급실 프로필을 함께 생성합니다. */
    @Transactional
    public SignupResponse signupHospital(HospitalSignupRequest request) {
        String contact = ContactPolicy.normalizeAndValidate(request.contact());
        String consentPolicyVersion = contactSharingConsentPolicy.requireAccepted(
                request.contactSharingConsentAccepted(),
                request.contactSharingConsentVersion()
        );
        InvitationCode invitation = requireUsableInvitation(
                request.invitationCode(),
                UserRole.HOSPITAL_STAFF,
                OrganizationType.HOSPITAL
        );
        Organization organization = organizationRepository.findLockedByPublicId(
                        invitation.getOrganization().getPublicId()
                )
                .orElseThrow(() -> new CustomException(ErrorCode.ORGANIZATION_NOT_FOUND));

        if (!organization.getName().equals(request.organizationName().trim())) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        if (hospitalProfileRepository.existsByOrganizationPublicId(organization.getPublicId())) {
            throw new CustomException(ErrorCode.USER_HOSPITAL_ACCOUNT_ALREADY_EXISTS);
        }

        String loginId = validateCredentials(request.loginId(), request.password());
        UserAccount account = saveAccount(organization, loginId, request.password(), UserRole.HOSPITAL_STAFF);
        HospitalProfile profile;
        try {
            profile = hospitalProfileRepository.saveAndFlush(HospitalProfile.create(
                    organization,
                    account,
                    request.address().trim(),
                    request.latitude(),
                    request.longitude(),
                    contact
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new CustomException(ErrorCode.USER_HOSPITAL_ACCOUNT_ALREADY_EXISTS);
        }

        recordContactConsent(account, consentPolicyVersion);
        consume(invitation, account);
        return SignupResponse.hospital(account, profile);
    }

    /** 구급대 조직에 소속된 개인 구급대원 계정을 생성합니다. */
    @Transactional
    public SignupResponse signupParamedic(ParamedicSignupRequest request) {
        String contact = ContactPolicy.normalizeAndValidate(request.contact());
        String consentPolicyVersion = contactSharingConsentPolicy.requireAccepted(
                request.contactSharingConsentAccepted(),
                request.contactSharingConsentVersion()
        );
        InvitationCode invitation = requireUsableInvitation(
                request.invitationCode(),
                UserRole.PARAMEDIC,
                OrganizationType.EMS_UNIT
        );
        String loginId = validateCredentials(request.loginId(), request.password());
        UserAccount account = saveAccount(
                invitation.getOrganization(),
                loginId,
                request.password(),
                UserRole.PARAMEDIC
        );
        paramedicProfileRepository.save(ParamedicProfile.create(
                account,
                invitation.getOrganization(),
                contact
        ));

        recordContactConsent(account, consentPolicyVersion);
        consume(invitation, account);
        return SignupResponse.paramedic(account);
    }

    private InvitationCode requireUsableInvitation(
            String plainCode,
            UserRole expectedRole,
            OrganizationType expectedOrganizationType
    ) {
        InvitationCode invitation = invitationCodeRepository.findLockedByCodeDigest(
                        secretDigester.digest(plainCode.trim())
                )
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_INVALID));

        if (invitation.getStatus() == InvitationStatus.USED) {
            throw new CustomException(ErrorCode.INVITATION_CODE_USED);
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new CustomException(ErrorCode.INVITATION_CODE_REVOKED);
        }
        if (invitation.getStatus() == InvitationStatus.EXPIRED || invitation.hasExpiredAt(clock.instant())) {
            throw new CustomException(ErrorCode.INVITATION_CODE_EXPIRED);
        }
        if (invitation.getRole() != expectedRole
                || invitation.getOrganization().getType() != expectedOrganizationType) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return invitation;
    }

    private String validateCredentials(String requestedLoginId, String password) {
        String loginId = AccountCredentialPolicy.normalizeAndValidateLoginId(requestedLoginId);
        AccountCredentialPolicy.validatePassword(password);
        if (userAccountRepository.existsByLoginId(loginId)) {
            throw new CustomException(ErrorCode.USER_LOGIN_ID_DUPLICATE);
        }
        return loginId;
    }

    private UserAccount saveAccount(
            Organization organization,
            String loginId,
            String password,
            UserRole role
    ) {
        try {
            return userAccountRepository.saveAndFlush(UserAccount.createMember(
                    organization,
                    loginId,
                    passwordEncoder.encode(password),
                    role
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new CustomException(ErrorCode.USER_LOGIN_ID_DUPLICATE);
        }
    }

    private void consume(InvitationCode invitation, UserAccount account) {
        Instant now = clock.instant();
        invitation.use(account, now);
        auditService.record(
                AuditAction.INVITATION_USED,
                account,
                account.getOrganization(),
                "INVITATION_CODE",
                invitation.getPublicId(),
                now
        );
    }

    private void recordContactConsent(UserAccount account, String policyVersion) {
        Instant consentedAt = clock.instant();
        ContactSharingConsent consent = contactSharingConsentRepository.save(
                ContactSharingConsent.record(account, policyVersion, consentedAt)
        );
        auditService.record(
                AuditAction.CONTACT_SHARING_CONSENT_RECORDED,
                account,
                account.getOrganization(),
                "CONTACT_SHARING_CONSENT",
                consent.getPublicId(),
                consentedAt
        );
    }
}
