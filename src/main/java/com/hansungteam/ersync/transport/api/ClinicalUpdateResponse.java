package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportUpdateCommandType;

import java.time.Instant;

/** 임상 원본 추가 결과와 현재 snapshot 반영 여부를 반환합니다. */
public record ClinicalUpdateResponse(
        String transportRequestId,
        TransportUpdateCommandType updateType,
        String recordId,
        Instant clinicalAt,
        Instant serverReceivedAt,
        boolean snapshotUpdated,
        Instant lastClinicalUpdateAt,
        boolean idempotentReplay
) {
}
