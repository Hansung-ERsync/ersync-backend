package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 병원 응답과 재전송의 의미가 같은지 비교할 SHA-256 지문을 만듭니다. */
@Component
public class HospitalCommandFingerprint {

    public byte[] accept() {
        return digest("ACCEPT");
    }

    public byte[] reject(HospitalRejectionReason reason, String detail) {
        return digest("REJECT|" + reason.name() + "|" + (detail == null ? "" : detail));
    }

    public byte[] retry(String transportRequestId) {
        return digest("RETRY|" + transportRequestId);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
