package com.hansungteam.ersync.clinical.api;

import com.hansungteam.ersync.clinical.domain.ClinicalValueState;
import com.hansungteam.ersync.clinical.domain.MeasurementUnavailableReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 다섯 항목을 모두 포함하는 활력징후 세트 입력 DTO입니다.
 */
public record VitalSignSetRequest(
        @NotNull @Valid BloodPressureItem bloodPressure,
        @NotNull @Valid IntegerVitalItem pulse,
        @NotNull @Valid IntegerVitalItem respiratoryRate,
        @NotNull @Valid TemperatureItem temperature,
        @NotNull @Valid IntegerVitalItem spo2,
        @NotNull @PastOrPresent Instant measuredAt,
        @NotNull Instant enteredAt,
        String supersedesVitalSignSetId,
        @Size(max = 255) String correctionReason
) {

    /**
     * 혈압 항목입니다.
     */
    public record BloodPressureItem(
            @NotNull ClinicalValueState state,
            @Min(0) @Max(300) Integer systolic,
            @Min(0) @Max(300) Integer diastolic,
            MeasurementUnavailableReason unavailableReason,
            @Size(max = 120) String otherDetail
    ) {
    }

    /**
     * 정수 단일값 활력징후 항목입니다.
     */
    public record IntegerVitalItem(
            @NotNull ClinicalValueState state,
            @Min(0) @Max(300) Integer value,
            MeasurementUnavailableReason unavailableReason,
            @Size(max = 120) String otherDetail
    ) {
    }

    /**
     * 체온 항목입니다.
     */
    public record TemperatureItem(
            @NotNull ClinicalValueState state,
            @DecimalMin("0.0") @DecimalMax("50.0") BigDecimal value,
            MeasurementUnavailableReason unavailableReason,
            @Size(max = 120) String otherDetail
    ) {
    }
}
