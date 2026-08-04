package com.hansungteam.ersync.paramedic.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.paramedic.api.ParamedicPrivacyConsentResponse;
import com.hansungteam.ersync.paramedic.api.ParamedicProfileResponse;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 인증된 구급대원 본인의 계정·소속·연락처·동의만 조회합니다. */
@Service
@RequiredArgsConstructor
public class ParamedicProfileQueryService {

    private final UserAccountRepository userAccountRepository;
    private final ParamedicProfileRepository paramedicProfileRepository;
    private final ContactSharingConsentRepository contactSharingConsentRepository;
    private final ContactSharingConsentPolicy contactSharingConsentPolicy;

    @Transactional(readOnly = true)
    public ParamedicProfileResponse getMine(AuthenticatedAccount authenticated) {
        if (authenticated.role() != UserRole.PARAMEDIC) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActiveParamedicContext(account, authenticated);

        ParamedicProfile profile = paramedicProfileRepository.findByAccountPublicId(account.getPublicId())
                .filter(found -> found.getOrganization().getPublicId()
                        .equals(account.getOrganization().getPublicId()))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<ContactSharingConsent> consents = contactSharingConsentRepository
                .findByAccountPublicIdOrderByConsentedAtAsc(account.getPublicId());

        return new ParamedicProfileResponse(
                account.getPublicId(),
                account.getLoginId(),
                profile.getDisplayName(),
                account.getOrganization().getPublicId(),
                account.getOrganization().getName(),
                account.getRole(),
                profile.getContact(),
                resolveConsent(consents)
        );
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

    private ParamedicPrivacyConsentResponse resolveConsent(List<ContactSharingConsent> consents) {
        ContactSharingConsent collectionUse = find(
                consents,
                ConsentType.CONTACT_COLLECTION_USE,
                contactSharingConsentPolicy.collectionUsePolicyVersion()
        );
        ContactSharingConsent hospitalProvision = find(
                consents,
                ConsentType.HOSPITAL_PROVISION,
                contactSharingConsentPolicy.hospitalProvisionPolicyVersion()
        );
        if (collectionUse != null && hospitalProvision != null) {
            Instant consentedAt = collectionUse.getConsentedAt().isAfter(hospitalProvision.getConsentedAt())
                    ? collectionUse.getConsentedAt()
                    : hospitalProvision.getConsentedAt();
            return new ParamedicPrivacyConsentResponse(
                    collectionUse.getPolicyVersion(),
                    hospitalProvision.getPolicyVersion(),
                    consentedAt,
                    false
            );
        }

        ContactSharingConsent combined = find(
                consents,
                ConsentType.CONTACT_COLLECTION_AND_PROVISION,
                contactSharingConsentPolicy.activePolicyVersion()
        );
        if (combined != null) {
            return new ParamedicPrivacyConsentResponse(
                    combined.getPolicyVersion(),
                    combined.getPolicyVersion(),
                    combined.getConsentedAt(),
                    true
            );
        }
        throw new CustomException(ErrorCode.USER_CONTACT_OR_CONSENT_REQUIRED);
    }

    private ContactSharingConsent find(
            List<ContactSharingConsent> consents,
            ConsentType consentType,
            String policyVersion
    ) {
        return consents.stream()
                .filter(consent -> consent.getConsentType() == consentType)
                .filter(consent -> consent.getPolicyVersion().equals(policyVersion))
                .findFirst()
                .orElse(null);
    }
}
