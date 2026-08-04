package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.privacy.domain.ConsentType;

/** 가입 화면이 표시하고 제출해야 하는 현재 개인정보 동의 버전입니다. */
public record RequiredConsentResponse(
        ConsentType type,
        String policyVersion
) {
}
