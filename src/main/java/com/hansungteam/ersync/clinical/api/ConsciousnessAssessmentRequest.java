package com.hansungteam.ersync.clinical.api;

import com.hansungteam.ersync.clinical.domain.Avpu;
import com.hansungteam.ersync.clinical.domain.ConsciousnessUnassessableReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * AVPU 의식 상태 입력 DTO입니다.
 */
public record ConsciousnessAssessmentRequest(
        @NotNull Avpu avpu,
        ConsciousnessUnassessableReason unassessableReason,
        @Size(max = 120) String unassessableDetail,
        @NotNull @PastOrPresent Instant observedAt,
        @NotNull Instant enteredAt,
        String supersedesAssessmentId,
        @Size(max = 255) String correctionReason
) {
}
