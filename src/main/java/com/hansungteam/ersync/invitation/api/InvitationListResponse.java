package com.hansungteam.ersync.invitation.api;

import org.springframework.data.domain.Page;

import java.util.List;

/** 가입 코드 메타데이터 목록의 페이지 응답입니다. */
public record InvitationListResponse(
        List<InvitationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static InvitationListResponse from(Page<InvitationResponse> result) {
        return new InvitationListResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
