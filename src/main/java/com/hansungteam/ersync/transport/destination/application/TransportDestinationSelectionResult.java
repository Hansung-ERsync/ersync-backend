package com.hansungteam.ersync.transport.destination.application;

import com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 목적지 선택 명령의 저장된 결과입니다. */
public record TransportDestinationSelectionResult(
        String transportRequestId,
        TransportRequestStatus transportRequestStatus,
        String selectedDestinationOfferId,
        String previousDestinationOfferId,
        TransportDestinationResultType resultType,
        Instant changedAt,
        boolean idempotentReplay
) {
}
