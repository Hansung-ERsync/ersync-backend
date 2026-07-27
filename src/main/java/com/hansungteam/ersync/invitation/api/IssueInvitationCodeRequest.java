package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 관리자 가입 코드 발급 요청 DTO입니다.
 */
public record IssueInvitationCodeRequest(
        @NotNull
        UserRole targetRole,

        @Min(1)
        @Max(30)
        Integer expiresInDays,

        @Future
        Instant expiresAt
) {
}
