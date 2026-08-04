package com.hansungteam.ersync.hospital.search.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 자동 병원 탐색의 반경·후보 수·대기시간 기준을 한곳에서 관리합니다. */
@Component
public class HospitalSearchPolicy {

    private final int initialRadiusKm;
    private final int minimumCandidateCount;
    private final int radiusIncrementKm;
    private final int maximumRadiusKm;
    private final Duration responseWindow;

    public HospitalSearchPolicy(
            @Value("${ersync.hospital-search.initial-radius-km:10}") int initialRadiusKm,
            @Value("${ersync.hospital-search.minimum-candidate-count:3}") int minimumCandidateCount,
            @Value("${ersync.hospital-search.radius-increment-km:10}") int radiusIncrementKm,
            @Value("${ersync.hospital-search.maximum-radius-km:100}") int maximumRadiusKm,
            @Value("${ersync.hospital-search.response-window:PT60S}") Duration responseWindow
    ) {
        if (initialRadiusKm <= 0
                || minimumCandidateCount <= 0
                || radiusIncrementKm <= 0
                || maximumRadiusKm < initialRadiusKm
                || responseWindow.isNegative()
                || responseWindow.isZero()) {
            throw new IllegalArgumentException("Hospital search policy values must be positive and ordered");
        }
        this.initialRadiusKm = initialRadiusKm;
        this.minimumCandidateCount = minimumCandidateCount;
        this.radiusIncrementKm = radiusIncrementKm;
        this.maximumRadiusKm = maximumRadiusKm;
        this.responseWindow = responseWindow;
    }

    public int initialRadiusKm() {
        return initialRadiusKm;
    }

    public int minimumCandidateCount() {
        return minimumCandidateCount;
    }

    public int radiusIncrementKm() {
        return radiusIncrementKm;
    }

    public int maximumRadiusKm() {
        return maximumRadiusKm;
    }

    public Duration responseWindow() {
        return responseWindow;
    }
}
