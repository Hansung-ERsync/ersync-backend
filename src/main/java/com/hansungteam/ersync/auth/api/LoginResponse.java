package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;

import java.time.Instant;

/**
 * 로그인 토큰 발급 응답 DTO입니다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        String refreshTokenId,
        Instant refreshTokenExpiresAt,
        AccountResponse account
) {

    public static LoginResponse from(AuthService.LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.tokenType(),
                result.expiresInSeconds(),
                result.refreshToken(),
                result.refreshTokenId(),
                result.refreshTokenExpiresAt(),
                AccountResponse.from(result.account())
        );
    }
}
