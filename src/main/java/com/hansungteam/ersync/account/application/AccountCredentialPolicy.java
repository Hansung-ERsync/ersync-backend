package com.hansungteam.ersync.account.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;

import java.util.regex.Pattern;

/** 가입과 bootstrap에 동일하게 적용할 자격정보 기술 제한입니다. */
public final class AccountCredentialPolicy {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("[a-z0-9]{4,30}");

    private AccountCredentialPolicy() {
    }

    /** 로그인 ID 앞뒤 공백을 제거하고 허용 형식을 검증합니다. */
    public static String normalizeAndValidateLoginId(String loginId) {
        if (loginId == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String normalized = loginId.trim();
        if (!LOGIN_ID_PATTERN.matcher(normalized).matches()) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return normalized;
    }

    /** 비밀번호 길이를 검증합니다. */
    public static void validatePassword(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
    }
}
