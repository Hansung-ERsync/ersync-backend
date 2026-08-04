package com.hansungteam.ersync.organization.api;

import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationStatus;
import com.hansungteam.ersync.organization.domain.OrganizationType;

import java.time.Instant;

/** 프론트에 노출하는 조직 정보입니다. */
public record OrganizationResponse(
        String organizationId,
        String name,
        OrganizationType type,
        OrganizationStatus status,
        Instant createdAt
) {

    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getPublicId(),
                organization.getName(),
                organization.getType(),
                organization.getStatus(),
                organization.getCreatedAt()
        );
    }
}
