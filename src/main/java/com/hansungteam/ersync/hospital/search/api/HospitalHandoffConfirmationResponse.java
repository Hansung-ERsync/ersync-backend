package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 현재 목적지 병원이 확인한 최종 인계 결과입니다. */
public record HospitalHandoffConfirmationResponse(
        String offerId,
        String transportRequestId,
        TransportRequestStatus status,
        Instant completedAt,
        boolean idempotentReplay
) {
}
