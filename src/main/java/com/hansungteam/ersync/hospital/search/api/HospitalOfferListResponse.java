package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;

import java.time.Instant;
import java.util.List;

/** 병원 대시보드 카드와 안정적인 페이지 정보를 반환합니다. */
public record HospitalOfferListResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Instant serverNow
) {

    public record Item(
            String offerId,
            String transportRequestId,
            Integer dispatchAttemptNumber,
            TransportRequestStatus transportRequestStatus,
            HospitalOfferStatus offerStatus,
            HospitalOutcome hospitalOutcome,
            Instant processedAt,
            boolean currentDestination,
            boolean canWithdraw,
            String ageStatus,
            Integer ageYears,
            String sex,
            String preKtasClassificationStatus,
            Integer preKtasLevel,
            String preKtasExceptionReason,
            Long straightLineDistanceMeters,
            RouteEstimateStatus routeEstimateStatus,
            Long routeDistanceMeters,
            Long etaSeconds,
            Long lastSuccessfulRouteDistanceMeters,
            Long lastSuccessfulEtaSeconds,
            Instant lastSuccessfulEtaCalculatedAt,
            Instant lastClinicalUpdateAt,
            Instant offeredAt,
            Instant respondedAt,
            HospitalAcceptanceWithdrawalReason withdrawalReason,
            String withdrawalDetail,
            Instant withdrawnAt,
            boolean canConfirmHandoff,
            Instant handoffRequestedAt,
            Instant completedAt,
            Instant cancelledAt,
            TransportCancellationReason cancellationReason
    ) {
    }
}
