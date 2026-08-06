package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.api.HospitalOutcome;

import java.time.Instant;

/** 병원별 표시 결과와 그 결과가 확정된 서버 시각입니다. */
public record HospitalOfferOutcomeResult(
        HospitalOutcome outcome,
        Instant processedAt
) {
}
