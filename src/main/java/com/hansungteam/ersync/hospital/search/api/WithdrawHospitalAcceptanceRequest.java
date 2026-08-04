package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 병원이 이미 한 수락을 철회하는 사유 입력입니다. */
public record WithdrawHospitalAcceptanceRequest(
        @NotNull HospitalAcceptanceWithdrawalReason reason,
        @Size(max = 200) String detail
) {
}
