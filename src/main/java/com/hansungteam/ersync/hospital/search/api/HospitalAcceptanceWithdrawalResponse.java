package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 병원 수락 철회의 저장된 멱등 결과입니다. */
public record HospitalAcceptanceWithdrawalResponse(
        TransportRequestStatus transportRequestStatus,
        HospitalAcceptanceWithdrawalReason reason,
        String detail,
        Instant withdrawnAt,
        boolean searchRestarted
) {
}
