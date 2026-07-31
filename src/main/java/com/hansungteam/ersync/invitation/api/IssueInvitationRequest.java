package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 슈퍼 관리자의 가입 코드 발급 요청입니다. */
public record IssueInvitationRequest(
        @NotBlank String organizationId,
        @NotNull UserRole role,
        @NotNull InvitationExpiryOption expiryOption,
        Instant customExpiresAt
) {
}
