package com.hansungteam.ersync.clinical.api;

import com.hansungteam.ersync.clinical.domain.TreatmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 현장 처치 또는 처치 시도 입력 DTO입니다.
 */
public record TreatmentEventRequest(
        @NotNull TreatmentType type,
        @NotNull @PastOrPresent Instant performedAt,
        @NotNull Instant enteredAt,
        @NotBlank @Size(max = 40) String detailSchemaVersion,
        @Size(max = 4000) String detailsJson,
        String supersedesTreatmentEventId,
        @Size(max = 255) String correctionReason
) {
}
