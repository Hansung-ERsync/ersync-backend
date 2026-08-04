package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.ClinicalRecordType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 권한 있는 사용자에게 최신 임상 요약과 append-only 원본 페이지를 반환합니다. */
public record ClinicalTimelineResponse(
        String transportRequestId,
        LatestSnapshot latestSnapshot,
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Instant serverNow
) {

    public record LatestSnapshot(
            PreKtas preKtas,
            Consciousness consciousness,
            VitalSigns vitalSigns,
            List<Treatment> treatments,
            Instant lastClinicalUpdateAt
    ) {
    }

    public record Item(
            ClinicalRecordType recordType,
            String recordId,
            Instant clinicalAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            PreKtas preKtas,
            Consciousness consciousness,
            VitalSigns vitalSigns,
            Treatment treatment
    ) {
    }

    public record PreKtas(
            String classificationStatus,
            Integer level,
            String exceptionReason,
            String exceptionDetail,
            Instant assessedAt,
            String standardVersion
    ) {
    }

    public record Consciousness(
            String avpu,
            String unassessableReason,
            String unassessableDetail,
            Instant observedAt
    ) {
    }

    public record VitalSigns(Instant measuredAt, List<VitalSign> measurements) {
    }

    public record VitalSign(
            String type,
            String state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            String unavailableReason,
            String unavailableDetail
    ) {
    }

    public record Treatment(
            String type,
            String attemptResult,
            Instant performedAt,
            TreatmentDetails details
    ) {
    }

    public record TreatmentDetails(
            String method,
            String device,
            BigDecimal flowRateLpm,
            Instant startedAt,
            Boolean success,
            String currentStatus,
            Boolean rosc,
            Instant roscAt,
            Integer shockCount,
            String fluidName,
            BigDecimal amountMl,
            String medicationName,
            String dose,
            String route,
            String site,
            Boolean tourniquetUsed,
            Instant tourniquetAppliedAt,
            String leadType,
            String findings,
            Boolean transmitted,
            Instant birthAt,
            String detail
    ) {
    }
}
