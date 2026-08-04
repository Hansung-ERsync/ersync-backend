package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 멱등한 이송 취소 결과입니다. */
public record TransportCancellationResponse(
        String transportRequestId,
        TransportRequestStatus status,
        TransportCancellationReason reason,
        String detail,
        Instant cancelledAt,
        boolean idempotentReplay
) {
}
