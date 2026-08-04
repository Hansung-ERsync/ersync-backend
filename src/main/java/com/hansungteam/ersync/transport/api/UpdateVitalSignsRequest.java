package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 이송 중 새 활력징후 다섯 항목을 append-only로 추가하는 입력입니다. */
public record UpdateVitalSignsRequest(
        @NotNull Instant measuredAt,
        @NotNull Instant enteredAt,
        @NotEmpty @Size(min = 5, max = 5) List<@NotNull @Valid VitalSignInput> measurements
) {

    public record VitalSignInput(
            @NotNull VitalSignType type,
            @NotNull VitalSignState state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            @Size(max = 200) String unavailableDetail
    ) {
    }
}
