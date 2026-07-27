package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.application.InvitationCodeService;
import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;

import java.time.Instant;

/**
 * 가입 코드 발급 응답 DTO입니다. 원문 코드는 이 응답에서만 제공됩니다.
 */
public record IssuedInvitationCodeResponse(
        String invitationCodeId,
        String organizationId,
        UserRole targetRole,
        InvitationCodeStatus status,
        Instant expiresAt,
        String plaintextCode
) {

    public static IssuedInvitationCodeResponse from(InvitationCodeService.IssuedInvitationResult result) {
        return new IssuedInvitationCodeResponse(
                result.invitationCodeId(),
                result.organizationId(),
                result.targetRole(),
                result.status(),
                result.expiresAt(),
                result.plaintextCode()
        );
    }
}
