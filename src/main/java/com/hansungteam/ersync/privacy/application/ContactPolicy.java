package com.hansungteam.ersync.privacy.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;

import java.util.regex.Pattern;

/** 병원과 구급대원 가입에서 공통으로 사용하는 회신 연락처 정책입니다. */
public final class ContactPolicy {

    private static final Pattern CONTACT_PATTERN = Pattern.compile("^[0-9+][0-9-]{7,29}$");

    private ContactPolicy() {
    }

    /** 앞뒤 공백을 제거하고 안전한 전화번호 형태인지 검증합니다. */
    public static String normalizeAndValidate(String requestedContact) {
        if (requestedContact == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String contact = requestedContact.trim();
        if (!CONTACT_PATTERN.matcher(contact).matches()) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return contact;
    }
}
