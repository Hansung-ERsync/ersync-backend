package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;

import java.time.Instant;
import java.util.List;

/** 가입 코드 원문 없이 소속·역할과 현재 동의 계약만 반환합니다. */
public record InvitationValidationResponse(
        String organizationId,
        String organizationName,
        UserRole role,
        Instant expiresAt,
        List<RequiredConsentResponse> requiredConsents
) {
}
