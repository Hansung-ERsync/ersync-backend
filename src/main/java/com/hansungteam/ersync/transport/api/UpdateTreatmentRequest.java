package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** 이송 중 실제 처치 또는 실패한 처치 시도 한 건을 추가하는 입력입니다. */
public record UpdateTreatmentRequest(
        @NotNull TreatmentType type,
        TreatmentAttemptResult attemptResult,
        @Valid TreatmentDetailsInput details,
        Instant performedAt,
        @NotNull Instant enteredAt
) {

    public record TreatmentDetailsInput(
            @Size(max = 100) String method,
            @Size(max = 100) String device,
            BigDecimal flowRateLpm,
            Instant startedAt,
            Boolean success,
            @Size(max = 50) String currentStatus,
            Boolean rosc,
            Instant roscAt,
            Integer shockCount,
            @Size(max = 100) String fluidName,
            BigDecimal amountMl,
            @Size(max = 100) String medicationName,
            @Size(max = 50) String dose,
            @Size(max = 50) String route,
            @Size(max = 100) String site,
            Boolean tourniquetUsed,
            Instant tourniquetAppliedAt,
            @Size(max = 20) String leadType,
            @Size(max = 200) String findings,
            Boolean transmitted,
            Instant birthAt,
            @Size(max = 300) String detail
    ) {
    }
}
