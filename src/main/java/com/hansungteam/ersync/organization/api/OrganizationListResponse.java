package com.hansungteam.ersync.organization.api;

import org.springframework.data.domain.Page;

import java.util.List;

/** 조직 목록의 페이지 응답입니다. */
public record OrganizationListResponse(
        List<OrganizationResponse> items,
        int totalPages
) {

    public static OrganizationListResponse from(Page<OrganizationResponse> result) {
        return new OrganizationListResponse(
                result.getContent(),
                result.getTotalPages()
        );
    }
}
