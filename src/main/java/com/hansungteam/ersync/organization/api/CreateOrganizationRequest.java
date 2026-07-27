package com.hansungteam.ersync.organization.api;

import com.hansungteam.ersync.organization.domain.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 조직 등록 요청 DTO입니다.
 */
public record CreateOrganizationRequest(
        @NotNull
        OrganizationType type,

        @NotBlank
        @Size(max = 120)
        String name
) {
}
