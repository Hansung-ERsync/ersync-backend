package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 병원이 판단에 사용하는 최소 임상정보이며 출발 좌표는 포함하지 않습니다. */
public record HospitalOfferDetailResponse(
        String offerId,
        String transportRequestId,
        int dispatchAttemptNumber,
        TransportRequestStatus transportRequestStatus,
        HospitalOfferStatus offerStatus,
        HospitalOutcome hospitalOutcome,
        Instant processedAt,
        boolean currentDestination,
        boolean canWithdraw,
        Patient patient,
        Incident incident,
        PreKtas preKtas,
        Consciousness consciousness,
        VitalSigns vitalSigns,
        List<Treatment> treatments,
        Requester requester,
        Route route,
        Timing timing,
        HospitalRejectionReason rejectionReason,
        String rejectionDetail,
        HospitalAcceptanceWithdrawalReason withdrawalReason,
        String withdrawalDetail,
        Instant respondedAt,
        Instant withdrawnAt,
        boolean canConfirmHandoff,
        Instant handoffRequestedAt,
        Instant completedAt,
        Instant cancelledAt,
        TransportCancellationReason cancellationReason,
        Instant serverNow
) {

    public record Patient(String ageStatus, Integer ageYears, String sex) {
    }

    public record Incident(
            String occurrenceType,
            String injuryMechanism,
            Set<String> injurySites,
            String primarySymptom,
            String primarySymptomDetail,
            Set<String> secondarySymptoms,
            String onsetTimeStatus,
            Instant onsetAt
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
            String method,
            String device,
            BigDecimal flowRateLpm,
            String currentStatus,
            String medicationName,
            String dose,
            String route,
            String site,
            String detail
    ) {
    }

    public record Requester(String organizationName, String callbackContact) {
    }

    public record Route(
            long straightLineDistanceMeters,
            RouteEstimateStatus status,
            Long routeDistanceMeters,
            Long etaSeconds,
            Instant calculatedAt,
            Long lastSuccessfulRouteDistanceMeters,
            Long lastSuccessfulEtaSeconds,
            Instant lastSuccessfulCalculatedAt
    ) {
    }

    public record Timing(
            Instant requestReceivedAt,
            Instant offeredAt,
            Instant lastClinicalUpdateAt
    ) {
    }
}
