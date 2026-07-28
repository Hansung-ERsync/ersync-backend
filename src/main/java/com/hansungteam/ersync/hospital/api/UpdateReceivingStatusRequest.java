package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 병원 응급실 수신 상태 변경 요청 DTO입니다.
 */
public record UpdateReceivingStatusRequest(
        @NotNull
        ReceivingStatus status
) {
}
