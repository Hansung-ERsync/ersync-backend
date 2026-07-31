package com.hansungteam.ersync.account.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 병원 공용 계정과 응급실 프로필 가입 요청입니다. */
public record HospitalSignupRequest(
        @NotBlank String invitationCode,
        @NotBlank @Size(max = 100) String organizationName,
        @NotBlank @Pattern(regexp = "[a-z0-9]{4,30}") String loginId,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 255) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotBlank @Size(max = 30) String contact
) {
}
