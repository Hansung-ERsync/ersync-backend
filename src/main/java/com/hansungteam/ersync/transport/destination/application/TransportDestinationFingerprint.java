package com.hansungteam.ersync.transport.destination.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 목적지 명령 원문을 보관하지 않고 같은 명령인지 판별할 SHA-256 지문을 만듭니다. */
@Component
public class TransportDestinationFingerprint {

    public byte[] digest(String transportRequestId, String offerId) {
        String value = "DESTINATION|" + transportRequestId + "|" + offerId;
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
