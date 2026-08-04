package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 이송 중 새 AVPU 의식 평가를 추가하는 입력입니다. */
public record UpdateConsciousnessRequest(
        @NotNull Avpu avpu,
        ConsciousnessUnassessableReason unassessableReason,
        @Size(max = 200) String unassessableDetail,
        @NotNull Instant observedAt,
        @NotNull Instant enteredAt
) {
}
