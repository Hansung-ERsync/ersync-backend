package com.hansungteam.ersync.paramedic.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.paramedic.api.ParamedicPrivacyConsentResponse;
import com.hansungteam.ersync.paramedic.api.ParamedicProfileResponse;
import com.hansungteam.ersync.paramedic.api.UpdateParamedicProfileRequest;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.application.ContactPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 인증된 구급대원 본인의 표시 이름과 병원 회신 연락처만 변경합니다. */
@Service
@RequiredArgsConstructor
public class ParamedicProfileCommandService {

    private final UserAccountRepository userAccountRepository;
    private final ParamedicProfileRepository paramedicProfileRepository;
    private final ParamedicProfileResponseAssembler responseAssembler;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public ParamedicProfileResponse update(
            AuthenticatedAccount authenticated,
            UpdateParamedicProfileRequest request
    ) {
        if (authenticated.role() != UserRole.PARAMEDIC) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        String displayName = ParamedicProfilePolicy.normalizeAndValidateDisplayName(request.displayName());
        String contact = ContactPolicy.normalizeAndValidate(request.callbackContact());

        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActiveParamedicContext(account, authenticated);

        ParamedicProfile profile = paramedicProfileRepository.findLockedByAccountPublicId(account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!profile.getAccount().getPublicId().equals(account.getPublicId())
                || !profile.getOrganization().getPublicId()
                .equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }

        ParamedicPrivacyConsentResponse privacyConsent = responseAssembler.resolveConsent(account);
        profile.updateDetails(displayName, contact);
        ParamedicProfile saved = paramedicProfileRepository.saveAndFlush(profile);
        auditService.record(
                AuditAction.PARAMEDIC_PROFILE_UPDATED,
                account,
                account.getOrganization(),
                "PARAMEDIC_PROFILE",
                profile.getPublicId(),
                clock.instant()
        );
        return responseAssembler.assemble(account, saved, privacyConsent);
    }

    private void requireActiveParamedicContext(
            UserAccount account,
            AuthenticatedAccount authenticated
    ) {
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        if (account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || authenticated.organizationId() == null
                || !authenticated.organizationId().equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
    }
}
