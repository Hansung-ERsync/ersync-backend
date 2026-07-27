package com.hansungteam.ersync.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 DTO입니다.
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 80)
        String loginId,

        @NotBlank
        @Size(max = 100)
        String password
) {
}
