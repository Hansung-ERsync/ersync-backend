package com.hansungteam.ersync.transport.application;

import org.springframework.stereotype.Component;

/** 갱신 종류까지 포함하여 endpoint 사이에서도 충돌 없는 SHA-256 지문을 계산합니다. */
@Component
public class TransportUpdateFingerprint {

    private final TransportRequestFingerprint fingerprint;

    public TransportUpdateFingerprint(TransportRequestFingerprint fingerprint) {
        this.fingerprint = fingerprint;
    }

    public byte[] digest(String commandType, Object request) {
        return fingerprint.digest(new Envelope(commandType, request));
    }

    private record Envelope(String commandType, Object request) {
    }
}
