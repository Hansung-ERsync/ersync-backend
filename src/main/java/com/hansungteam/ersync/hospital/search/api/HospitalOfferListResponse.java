package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

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
            int dispatchAttemptNumber,
            TransportRequestStatus transportRequestStatus,
            HospitalOfferStatus offerStatus,
            String ageStatus,
            Integer ageYears,
            String sex,
            String preKtasClassificationStatus,
            Integer preKtasLevel,
            String preKtasExceptionReason,
            long straightLineDistanceMeters,
            RouteEstimateStatus routeEstimateStatus,
            Long routeDistanceMeters,
            Long etaSeconds,
            Instant offeredAt
    ) {
    }
}
