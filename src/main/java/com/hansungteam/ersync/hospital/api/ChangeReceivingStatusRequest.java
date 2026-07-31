package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import jakarta.validation.constraints.NotNull;

/** 병원 응급실의 신규 요청 수신 상태 변경 요청입니다. */
public record ChangeReceivingStatusRequest(@NotNull ReceivingStatus status) {
}
