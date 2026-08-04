package com.hansungteam.ersync.paramedic.api;

import com.hansungteam.ersync.global.security.UserRole;

/** 로그인·토큰 복구 후 Flutter 화면을 구성하는 본인 프로필입니다. */
public record ParamedicProfileResponse(
        String accountId,
        String loginId,
        String displayName,
        String organizationId,
        String organizationName,
        UserRole role,
        String callbackContact,
        ParamedicPrivacyConsentResponse privacyConsent
) {
}
