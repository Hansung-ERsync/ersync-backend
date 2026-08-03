package com.hansungteam.ersync.privacy.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 클라이언트가 표시한 연락처 제공 동의 문구 버전을 서버 기준과 대조합니다. */
@Component
public class ContactSharingConsentPolicy {

    private final String activePolicyVersion;

    public ContactSharingConsentPolicy(
            @Value("${ersync.privacy.contact-sharing-consent-version}") String activePolicyVersion
    ) {
        this.activePolicyVersion = activePolicyVersion;
    }

    /** 명시적 동의와 현재 문구 버전이 모두 일치할 때 저장할 버전을 반환합니다. */
    public String requireAccepted(boolean accepted, String requestedPolicyVersion) {
        if (!accepted
                || requestedPolicyVersion == null
                || !activePolicyVersion.equals(requestedPolicyVersion.trim())) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return activePolicyVersion;
    }

    public String activePolicyVersion() {
        return activePolicyVersion;
    }
}
