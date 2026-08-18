package com.hansungteam.ersync.hospital.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 로그인한 병원 공용 계정이 자기 응급실 위치와 연락처를 전체 수정하는 요청입니다. */
public record UpdateHospitalProfileRequest(
        @NotBlank @Size(max = 255) String address,
        @Size(max = 200) String detailAddress,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotBlank @Size(max = 30) String contact
) {
}
