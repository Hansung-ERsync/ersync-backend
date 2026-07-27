package com.hansungteam.ersync.organization.api;

import com.hansungteam.ersync.organization.application.OrganizationService;
import com.hansungteam.ersync.organization.domain.OrganizationType;

/**
 * 조직 응답 DTO입니다.
 */
public record OrganizationResponse(
        String organizationId,
        OrganizationType type,
        String name,
        boolean active
) {

    public static OrganizationResponse from(OrganizationService.OrganizationResult result) {
        return new OrganizationResponse(
                result.organizationId(),
                result.type(),
                result.name(),
                result.active()
        );
    }
}
