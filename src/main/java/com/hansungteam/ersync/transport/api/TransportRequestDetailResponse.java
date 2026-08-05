package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;
import java.util.List;

/** 앱 재실행 시 구급대원 본인의 진행 중 환자 화면을 복구하는 최신 상세입니다. */
public record TransportRequestDetailResponse(
        String transportRequestId,
        TransportRequestStatus status,
        String assessmentProtocolVersion,
        Patient patient,
        Incident incident,
        ClinicalTimelineResponse.LatestSnapshot latestSnapshot,
        Instant createdAt,
        Instant serverNow
) {

    public record Patient(String ageStatus, Integer ageYears, String sex) {
    }

    public record Incident(
            String occurrenceType,
            String occurrenceDetail,
            String injuryMechanism,
            List<String> injurySites,
            String primarySymptom,
            String primarySymptomDetail,
            List<String> secondarySymptoms,
            String onsetTimeStatus,
            Instant onsetAt
    ) {
    }
}
