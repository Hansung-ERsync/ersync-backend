package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.transport.domain.LocationFreshness;

import java.math.BigDecimal;
import java.time.Instant;

/** 권한 있는 사용자에게 최신 위치와 freshness·현재 목적지 ETA를 반환합니다. */
public record TransportLocationResponse(
        String transportRequestId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant capturedAt,
        Instant lastReceivedAt,
        LocationFreshness freshness,
        Long ageSeconds,
        Instant serverNow,
        Boolean locationReplaced,
        RouteEstimateStatus routeEstimateStatus,
        Long routeDistanceMeters,
        Long etaSeconds,
        Instant etaCalculatedAt,
        Long lastSuccessfulRouteDistanceMeters,
        Long lastSuccessfulEtaSeconds,
        Instant lastSuccessfulEtaCalculatedAt,
        boolean idempotentReplay
) {
}
