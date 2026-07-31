package com.hansungteam.ersync.global.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** 고난도 임의 원문을 만들고 저장용 SHA-256 다이제스트를 계산합니다. */
@Component
public class SecretDigester {

    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedSecret generate() {
        byte[] randomBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(randomBytes);
        String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GeneratedSecret(plainText, digest(plainText));
    }

    public byte[] digest(String plainText) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(plainText.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available", ex);
        }
    }
}
