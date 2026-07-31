package com.hansungteam.ersync.organization.api;

import com.hansungteam.ersync.organization.domain.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 슈퍼 관리자의 조직 등록 요청입니다. */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull OrganizationType type
) {
}
