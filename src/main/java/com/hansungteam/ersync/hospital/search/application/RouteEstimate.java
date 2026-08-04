package com.hansungteam.ersync.hospital.search.application;

/** 외부 지도 제공자가 반환한 도로 거리와 초 단위 ETA입니다. */
public record RouteEstimate(long distanceMeters, long etaSeconds) {
}
