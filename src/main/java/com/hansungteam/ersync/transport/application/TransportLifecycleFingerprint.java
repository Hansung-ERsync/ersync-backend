package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 종료 명령 종류와 payload를 포함한 SHA-256 지문을 만듭니다. */
@Component
public class TransportLifecycleFingerprint {

    public byte[] cancel(String requestId, TransportCancellationReason reason, String detail) {
        return digest("CANCEL|" + requestId + "|" + reason.name() + "|" + value(detail));
    }

    public byte[] handoffRequest(String requestId) {
        return digest("HANDOFF_REQUEST|" + requestId);
    }

    public byte[] handoffConfirm(String requestId, String offerId) {
        return digest("HANDOFF_CONFIRM|" + requestId + "|" + offerId);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
