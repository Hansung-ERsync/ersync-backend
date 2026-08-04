package com.hansungteam.ersync.transport.destination.api;

import com.hansungteam.ersync.transport.destination.application.TransportDestinationSelectionResult;
import com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 목적지 선택·변경 명령의 외부 응답입니다. */
public record TransportDestinationResponse(
        String transportRequestId,
        TransportRequestStatus transportRequestStatus,
        String selectedDestinationOfferId,
        String previousDestinationOfferId,
        TransportDestinationResultType resultType,
        Instant changedAt,
        boolean idempotentReplay
) {
    public static TransportDestinationResponse from(TransportDestinationSelectionResult result) {
        return new TransportDestinationResponse(
                result.transportRequestId(),
                result.transportRequestStatus(),
                result.selectedDestinationOfferId(),
                result.previousDestinationOfferId(),
                result.resultType(),
                result.changedAt(),
                result.idempotentReplay()
        );
    }
}
