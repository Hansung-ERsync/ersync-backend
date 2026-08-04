package com.hansungteam.ersync.privacy.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 클라이언트가 표시한 연락처 제공 동의 문구 버전을 서버 기준과 대조합니다. */
@Component
public class ContactSharingConsentPolicy {

    private final String combinedPolicyVersion;
    private final String collectionUsePolicyVersion;
    private final String hospitalProvisionPolicyVersion;

    public ContactSharingConsentPolicy(
            @Value("${ersync.privacy.contact-sharing-consent-version}") String combinedPolicyVersion,
            @Value("${ersync.privacy.collection-use-consent-version}") String collectionUsePolicyVersion,
            @Value("${ersync.privacy.hospital-provision-consent-version}") String hospitalProvisionPolicyVersion
    ) {
        this.combinedPolicyVersion = combinedPolicyVersion;
        this.collectionUsePolicyVersion = collectionUsePolicyVersion;
        this.hospitalProvisionPolicyVersion = hospitalProvisionPolicyVersion;
    }

    /** 병원 가입의 통합 동의와 현재 문구 버전이 일치할 때 저장할 버전을 반환합니다. */
    public String requireAccepted(boolean accepted, String requestedPolicyVersion) {
        return requireVersion(accepted, requestedPolicyVersion, combinedPolicyVersion);
    }

    /** 구급대원 가입에 필요한 수집·이용과 병원 제공 동의를 각각 검증합니다. */
    public ParamedicConsentVersions requireParamedicAccepted(
            boolean collectionUseAccepted,
            String requestedCollectionUseVersion,
            boolean hospitalProvisionAccepted,
            String requestedHospitalProvisionVersion
    ) {
        String collectionUse = requireVersion(
                collectionUseAccepted,
                requestedCollectionUseVersion,
                collectionUsePolicyVersion
        );
        String hospitalProvision = requireVersion(
                hospitalProvisionAccepted,
                requestedHospitalProvisionVersion,
                hospitalProvisionPolicyVersion
        );
        return new ParamedicConsentVersions(collectionUse, hospitalProvision);
    }

    public String activePolicyVersion() {
        return combinedPolicyVersion;
    }

    public String collectionUsePolicyVersion() {
        return collectionUsePolicyVersion;
    }

    public String hospitalProvisionPolicyVersion() {
        return hospitalProvisionPolicyVersion;
    }

    private String requireVersion(boolean accepted, String requestedVersion, String activeVersion) {
        if (!accepted || requestedVersion == null || !activeVersion.equals(requestedVersion.trim())) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return activeVersion;
    }
}
