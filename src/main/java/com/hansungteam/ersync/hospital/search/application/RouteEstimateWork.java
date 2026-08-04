package com.hansungteam.ersync.hospital.search.application;

import java.math.BigDecimal;

/** 짧은 DB 트랜잭션에서 꺼낸 외부 ETA 호출용 좌표 작업입니다. */
public record RouteEstimateWork(
        Long offerId,
        int attemptCount,
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude
) {
}
