package com.hansungteam.ersync.paramedic.api;

import java.time.Instant;

/** 구급대원 앱이 표시할 연락처 동의 버전과 동의 시각입니다. */
public record ParamedicPrivacyConsentResponse(
        String collectionUsePolicyVersion,
        String hospitalProvisionPolicyVersion,
        Instant consentedAt,
        boolean legacyCombined
) {
}
