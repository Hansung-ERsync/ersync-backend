package com.hansungteam.ersync.paramedic.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;

/** 구급대원 화면 표시 이름의 공통 정규화와 길이 제한입니다. */
public final class ParamedicProfilePolicy {

    private static final int DISPLAY_NAME_MIN_LENGTH = 2;
    private static final int DISPLAY_NAME_MAX_LENGTH = 50;

    private ParamedicProfilePolicy() {
    }

    public static String normalizeAndValidateDisplayName(String requestedDisplayName) {
        if (requestedDisplayName == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String displayName = requestedDisplayName.trim();
        if (displayName.length() < DISPLAY_NAME_MIN_LENGTH
                || displayName.length() > DISPLAY_NAME_MAX_LENGTH
                || displayName.chars().anyMatch(Character::isISOControl)) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return displayName;
    }
}
