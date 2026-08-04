package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 후보 소진 뒤 새 병원 탐색 회차 생성 결과입니다. */
public record DispatchAttemptResponse(
        String transportRequestId,
        TransportRequestStatus transportRequestStatus,
        String dispatchAttemptId,
        int attemptNumber,
        HospitalDispatchAttemptStatus attemptStatus,
        int currentRadiusKm,
        Instant nextExpansionAt,
        boolean idempotentReplay
) {
}
