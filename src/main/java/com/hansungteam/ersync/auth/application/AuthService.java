package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.account.application.AccountCredentialPolicy;
import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.auth.api.AuthTokenResponse;
import com.hansungteam.ersync.auth.api.LoginRequest;
import com.hansungteam.ersync.auth.domain.RefreshToken;
import com.hansungteam.ersync.auth.infrastructure.RefreshTokenRepository;
import com.hansungteam.ersync.global.crypto.GeneratedSecret;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 자격정보 로그인과 일회성 Refresh Token 회전을 수행합니다. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretDigester secretDigester;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    @Value("${ersync.auth.refresh-token-ttl:P7D}")
    private Duration refreshTokenTtl;

    /** 활성 계정의 자격정보를 검증하고 Access·Refresh Token을 발급합니다. */
    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String loginId = normalizedLoginIdOrInvalidCredentials(request.loginId());
        UserAccount account = userAccountRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_CREDENTIALS_INVALID));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new CustomException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        account.recordLogin(clock.instant());
        return issueTokenPair(account);
    }

    /** 사용 가능한 Refresh Token을 한 번 소비하고 새 토큰 쌍으로 교체합니다. */
    @Transactional
    public AuthTokenResponse refresh(String plainRefreshToken) {
        RefreshToken current = refreshTokenRepository.findLockedByTokenDigest(
                        secretDigester.digest(plainRefreshToken.trim())
                )
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        Instant now = clock.instant();
        if (!current.isUsableAt(now)) {
            throw new CustomException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        UserAccount account = current.getAccount();
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        GeneratedSecret generated = secretDigester.generate();
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        RefreshToken replacement = refreshTokenRepository.save(RefreshToken.issue(
                account,
                generated.digest(),
                refreshExpiresAt
        ));
        current.markUsed(now, replacement.getPublicId());

        return AuthTokenResponse.of(
                jwtTokenService.issue(account),
                generated.plainText(),
                refreshExpiresAt,
                account
        );
    }

    private AuthTokenResponse issueTokenPair(UserAccount account) {
        GeneratedSecret generated = secretDigester.generate();
        Instant refreshExpiresAt = clock.instant().plus(refreshTokenTtl);
        refreshTokenRepository.save(RefreshToken.issue(account, generated.digest(), refreshExpiresAt));
        return AuthTokenResponse.of(
                jwtTokenService.issue(account),
                generated.plainText(),
                refreshExpiresAt,
                account
        );
    }

    private String normalizedLoginIdOrInvalidCredentials(String loginId) {
        try {
            return AccountCredentialPolicy.normalizeAndValidateLoginId(loginId);
        } catch (CustomException ex) {
            throw new CustomException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
    }
}
