package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 구급대원이 현재 목적지 병원에 보낸 인계 확인 요청 결과입니다. */
public record TransportHandoffRequestResponse(
        String transportRequestId,
        TransportRequestStatus status,
        String destinationOfferId,
        String destinationHospitalName,
        Instant handoffRequestedAt,
        boolean idempotentReplay
) {
}
