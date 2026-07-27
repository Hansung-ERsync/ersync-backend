package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.application.InvitationCodeService;
import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;

import java.time.Instant;

/**
 * 원문 코드를 제외한 가입 코드 조회 응답 DTO입니다.
 */
public record InvitationCodeResponse(
        String invitationCodeId,
        String organizationId,
        String organizationName,
        UserRole targetRole,
        InvitationCodeStatus status,
        Instant expiresAt,
        Instant issuedAt,
        String usedBy,
        Instant usedAt,
        String revokedBy,
        Instant revokedAt
) {

    public static InvitationCodeResponse from(InvitationCodeService.InvitationResult result) {
        return new InvitationCodeResponse(
                result.invitationCodeId(),
                result.organizationId(),
                result.organizationName(),
                result.targetRole(),
                result.status(),
                result.expiresAt(),
                result.issuedAt(),
                result.usedBy(),
                result.usedAt(),
                result.revokedBy(),
                result.revokedAt()
        );
    }
}
