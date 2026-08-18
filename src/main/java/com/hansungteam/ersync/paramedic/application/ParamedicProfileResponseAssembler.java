package com.hansungteam.ersync.paramedic.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.paramedic.api.ParamedicPrivacyConsentResponse;
import com.hansungteam.ersync.paramedic.api.ParamedicProfileResponse;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 구급대원 조회와 수정 응답에 동일한 연락처 동의 해석을 적용합니다. */
@Component
@RequiredArgsConstructor
public class ParamedicProfileResponseAssembler {

    private final ContactSharingConsentRepository contactSharingConsentRepository;
    private final ContactSharingConsentPolicy contactSharingConsentPolicy;

    public ParamedicProfileResponse assemble(UserAccount account, ParamedicProfile profile) {
        return assemble(account, profile, resolveConsent(account));
    }

    public ParamedicProfileResponse assemble(
            UserAccount account,
            ParamedicProfile profile,
            ParamedicPrivacyConsentResponse privacyConsent
    ) {
        return new ParamedicProfileResponse(
                account.getPublicId(),
                account.getLoginId(),
                profile.getDisplayName(),
                account.getOrganization().getPublicId(),
                account.getOrganization().getName(),
                account.getRole(),
                profile.getContact(),
                privacyConsent
        );
    }

    public ParamedicPrivacyConsentResponse resolveConsent(UserAccount account) {
        List<ContactSharingConsent> consents = contactSharingConsentRepository
                .findByAccountPublicIdOrderByConsentedAtAsc(account.getPublicId());
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
