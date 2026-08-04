package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;
import java.util.List;

/** 구급대원이 자기 요청의 병원 탐색·응답 상태를 복구하는 권위 조회입니다. */
public record TransportHospitalSearchResponse(
        String transportRequestId,
        TransportRequestStatus status,
        Attempt currentAttempt,
        String exhaustionReason,
        List<Offer> offers,
        Instant serverNow
) {

    public record Attempt(
            String dispatchAttemptId,
            int number,
            HospitalDispatchAttemptStatus status,
            int currentRadiusKm,
            boolean candidateShortage,
            Instant nextExpansionAt,
            Instant startedAt,
            Instant endedAt
    ) {
    }

    public record Offer(
            String offerId,
            int dispatchAttemptNumber,
            String hospitalName,
            String hospitalContact,
            HospitalOfferStatus status,
            long straightLineDistanceMeters,
            RouteEstimateStatus routeEstimateStatus,
            Long routeDistanceMeters,
            Long etaSeconds,
            Instant etaCalculatedAt,
            HospitalRejectionReason rejectionReason,
            String rejectionDetail,
            Instant offeredAt,
            Instant respondedAt,
            Instant closedAt
    ) {
    }
}
