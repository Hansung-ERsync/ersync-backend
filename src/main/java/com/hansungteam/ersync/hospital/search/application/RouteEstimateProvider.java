package com.hansungteam.ersync.hospital.search.application;

import java.math.BigDecimal;

/** 후보 선정과 분리된 실제 도로 거리·ETA 제공자 계약입니다. */
public interface RouteEstimateProvider {

    RouteEstimate estimate(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude
    );
}
