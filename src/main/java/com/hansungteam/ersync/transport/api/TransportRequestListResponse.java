package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;
import java.util.List;

/** 환자정보 없이 본인 이송 상태와 목적지 요약만 반환하는 페이지입니다. */
public record TransportRequestListResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record Item(
            String transportRequestId,
            TransportRequestStatus status,
            String hospitalName,
            Instant createdAt,
            Instant statusUpdatedAt,
            Instant handoffRequestedAt,
            Instant completedAt,
            Instant cancelledAt,
            TransportCancellationReason cancellationReason
    ) {
    }
}
