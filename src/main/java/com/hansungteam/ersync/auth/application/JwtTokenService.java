package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 계정의 현재 권한 범위를 담은 짧은 수명의 Access Token을 발급합니다. */
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${ersync.auth.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${ersync.auth.jwt-issuer:ersync}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public IssuedAccessToken issue(UserAccount account) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(account.getPublicId())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", account.getRole().name());
        if (account.getOrganization() != null) {
            claims.claim("organizationId", account.getOrganization().getPublicId());
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims.build()))
                .getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
