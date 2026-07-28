package com.hansungteam.ersync.clinical.api;

import com.hansungteam.ersync.clinical.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.clinical.domain.PreKtasExceptionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Pre-KTAS 완료 또는 긴급 미완료 입력 DTO입니다.
 */
public record PreKtasAssessmentRequest(
        @NotNull PreKtasClassificationStatus classificationStatus,
        Integer level,
        PreKtasExceptionReason exceptionReason,
        @Size(max = 120) String exceptionDetail,
        @NotNull @PastOrPresent Instant assessedAt,
        @NotBlank @Size(max = 80) String standardVersion,
        @NotNull Instant enteredAt,
        String supersedesAssessmentId,
        @Size(max = 255) String correctionReason
) {
}
