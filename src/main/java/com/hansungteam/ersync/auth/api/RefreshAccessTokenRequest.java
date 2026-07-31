package com.hansungteam.ersync.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Refresh Token을 이용한 토큰 재발급 요청입니다. */
public record RefreshAccessTokenRequest(@NotBlank String refreshToken) {
}
