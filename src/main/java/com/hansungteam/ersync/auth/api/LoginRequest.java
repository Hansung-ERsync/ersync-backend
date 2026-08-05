package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.global.security.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 로그인 자격정보 요청입니다. */
public record LoginRequest(
        @NotBlank @Size(max = 30) String loginId,
        @NotBlank @Size(max = 64) String password,
        @NotNull UserRole role
) {
}
