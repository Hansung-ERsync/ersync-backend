package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.OrganizationType;

import java.time.Instant;

/**
 * 가입 코드가 가리키는 조직과 역할 확인 응답 DTO입니다.
 */
public record InvitationCodeVerificationResponse(
        String organizationId,
        String organizationName,
        OrganizationType organizationType,
        UserRole targetRole,
        Instant expiresAt
) {

    public static InvitationCodeVerificationResponse from(AuthService.InvitationPreviewResult result) {
        return new InvitationCodeVerificationResponse(
                result.organizationId(),
                result.organizationName(),
                result.organizationType(),
                result.targetRole(),
                result.expiresAt()
        );
    }
}
