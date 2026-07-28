package com.hansungteam.ersync.hospital.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 병원 응급실 프로필 등록·수정 요청 DTO입니다.
 */
public record UpsertHospitalProfileRequest(
        @NotBlank
        @Size(max = 255)
        String erAddress,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude,

        @NotBlank
        @Size(max = 40)
        String erContact
) {
}
