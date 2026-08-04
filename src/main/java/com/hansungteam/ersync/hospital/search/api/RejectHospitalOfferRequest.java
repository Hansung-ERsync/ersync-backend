package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 병원 거절 사유와 OTHER의 짧은 설명입니다. */
public record RejectHospitalOfferRequest(
        @NotNull HospitalRejectionReason reason,
        @Size(max = 200) String detail
) {
}
