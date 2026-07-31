package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.organization.domain.OrganizationType;

import java.time.Instant;

/** 원문과 다이제스트를 제외한 가입 코드 메타데이터입니다. */
public record InvitationResponse(
        String invitationCodeId,
        String organizationId,
        String organizationName,
        OrganizationType organizationType,
        UserRole role,
        InvitationStatus status,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        Instant createdAt
) {

    public static InvitationResponse from(InvitationCode invitation) {
        return new InvitationResponse(
                invitation.getPublicId(),
                invitation.getOrganization().getPublicId(),
                invitation.getOrganization().getName(),
                invitation.getOrganization().getType(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getUsedAt(),
                invitation.getRevokedAt(),
                invitation.getCreatedAt()
        );
    }
}
