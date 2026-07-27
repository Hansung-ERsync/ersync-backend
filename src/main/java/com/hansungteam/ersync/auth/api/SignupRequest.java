package com.hansungteam.ersync.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 코드 기반 회원가입 요청 DTO입니다.
 */
public record SignupRequest(
        @NotBlank
        @Size(max = 120)
        String invitationCode,

        @NotBlank
        @Size(min = 4, max = 80)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$")
        String loginId,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}
