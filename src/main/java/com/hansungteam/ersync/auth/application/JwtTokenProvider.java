package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * ERSync API의 HMAC 서명 Access Token을 생성하고 검증합니다.
 */
@Component
public class JwtTokenProvider {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration accessTokenTtl;

    public JwtTokenProvider(
            @Value("${ersync.security.jwt.secret}") String secret,
            @Value("${ersync.security.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public String createAccessToken(AuthenticatedAccount account, Instant now) {
        Instant expiresAt = now.plus(accessTokenTtl);
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payload(account, expiresAt));
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public AuthenticatedAccount parse(String token, Instant now) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new CustomException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new CustomException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
        }

        String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(extract(payload, "exp")));
        if (!expiresAt.isAfter(now)) {
            throw new CustomException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
        }

        String organizationId = extractNullable(payload, "organizationId");
        return new AuthenticatedAccount(
                extract(payload, "sub"),
                organizationId,
                UserRole.valueOf(extract(payload, "role")),
                extract(payload, "loginId")
        );
    }

    public long accessTokenExpiresInSeconds() {
        return accessTokenTtl.toSeconds();
    }

    private String payload(AuthenticatedAccount account, Instant expiresAt) {
        String organization = account.organizationId() == null
                ? "null"
                : "\"" + account.organizationId() + "\"";
        return "{"
                + "\"sub\":\"" + account.accountId() + "\","
                + "\"loginId\":\"" + account.loginId() + "\","
                + "\"role\":\"" + account.role().name() + "\","
                + "\"organizationId\":" + organization + ","
                + "\"exp\":" + expiresAt.getEpochSecond()
                + "}";
    }

    private String base64Url(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < leftBytes.length; index++) {
            result |= leftBytes[index] ^ rightBytes[index];
        }
        return result == 0;
    }

    private String extract(String payload, String name) {
        String marker = "\"" + name + "\":";
        int start = payload.indexOf(marker);
        if (start < 0) {
            throw new CustomException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
        }
        int valueStart = start + marker.length();
        if (payload.charAt(valueStart) == '"') {
            int valueEnd = payload.indexOf('"', valueStart + 1);
            return payload.substring(valueStart + 1, valueEnd);
        }
        int valueEnd = payload.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = payload.indexOf('}', valueStart);
        }
        return payload.substring(valueStart, valueEnd);
    }

    private String extractNullable(String payload, String name) {
        String value = extract(payload, name);
        return "null".equals(value) ? null : value;
    }
}
