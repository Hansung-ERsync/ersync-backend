package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.auth.application.IssuedAccessToken;
import com.hansungteam.ersync.global.security.UserRole;

import java.time.Instant;

/** 로그인과 토큰 갱신이 공통으로 반환하는 인증정보입니다. */
public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String accountId,
        String organizationId,
        UserRole role
) {

    public static AuthTokenResponse of(
            IssuedAccessToken accessToken,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserAccount account
    ) {
        return new AuthTokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken,
                refreshTokenExpiresAt,
                account.getPublicId(),
                account.getOrganization() == null ? null : account.getOrganization().getPublicId(),
                account.getRole()
        );
    }
}
