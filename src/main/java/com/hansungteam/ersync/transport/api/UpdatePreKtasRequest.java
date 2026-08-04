package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 이송 요청에 고정된 프로토콜 아래 새 Pre-KTAS 평가를 추가하는 입력입니다. */
public record UpdatePreKtasRequest(
        @NotNull PreKtasClassificationStatus classificationStatus,
        @DecimalMin("1") @DecimalMax("5") Integer level,
        PreKtasExceptionReason exceptionReason,
        @Size(max = 200) String exceptionDetail,
        Instant assessedAt,
        @NotBlank @Size(max = 50) String standardVersion,
        @NotNull Instant enteredAt
) {
}
