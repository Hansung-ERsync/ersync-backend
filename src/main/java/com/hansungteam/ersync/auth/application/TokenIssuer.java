package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.auth.infrastructure.RefreshTokenEntity;
import com.hansungteam.ersync.auth.infrastructure.RefreshTokenRepository;
import com.hansungteam.ersync.auth.infrastructure.UserAccountEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Access Token과 해시 저장용 Refresh Token을 함께 발급합니다.
 */
@Component
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenIssuer(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ersync.security.jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public IssuedToken issue(UserAccountEntity account, Instant now) {
        AuthenticatedAccount authenticatedAccount = new AuthenticatedAccount(
                account.id(),
                account.organization() == null ? null : account.organization().id(),
                account.role(),
                account.loginId()
        );
        String accessToken = jwtTokenProvider.createAccessToken(authenticatedAccount, now);
        String refreshToken = createOpaqueToken();
        RefreshTokenEntity entity = new RefreshTokenEntity(
                account,
                passwordEncoder.encode(refreshToken),
                now.plus(refreshTokenTtl),
                now
        );
        RefreshTokenEntity saved = refreshTokenRepository.save(entity);
        return new IssuedToken(
                accessToken,
                jwtTokenProvider.accessTokenExpiresInSeconds(),
                refreshToken,
                saved.id(),
                saved.expiresAt()
        );
    }

    public String createOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedToken(
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            String refreshTokenId,
            Instant refreshTokenExpiresAt
    ) {
    }
}
