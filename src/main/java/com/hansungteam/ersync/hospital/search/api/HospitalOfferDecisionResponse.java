package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 수락·거절 뒤 권위 상태와 멱등 재사용 여부를 반환합니다. */
public record HospitalOfferDecisionResponse(
        String offerId,
        HospitalOfferStatus offerStatus,
        String transportRequestId,
        TransportRequestStatus transportRequestStatus,
        Instant respondedAt,
        boolean idempotentReplay
) {
}
