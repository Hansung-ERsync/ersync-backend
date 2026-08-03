package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;

import java.util.regex.Pattern;

/** 계정 범위 멱등성 키의 길이와 안전한 문자 집합을 검증합니다. */
public final class IdempotencyKeyPolicy {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    private IdempotencyKeyPolicy() {
    }

    public static String normalizeAndValidate(String value) {
        if (value == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String normalized = value.trim();
        if (!PATTERN.matcher(normalized).matches()) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return normalized;
    }
}
