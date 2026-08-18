package com.hansungteam.ersync.hospital.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;

/** 병원 가입과 자기 프로필 수정에서 공유하는 주소 정규화 정책입니다. */
public final class HospitalProfilePolicy {

    private static final int ADDRESS_MAX_LENGTH = 255;
    private static final int DETAIL_ADDRESS_MAX_LENGTH = 200;

    private HospitalProfilePolicy() {
    }

    /** 필수 기본주소의 앞뒤 공백을 제거하고 저장 가능한 길이인지 검증합니다. */
    public static String normalizeAndValidateAddress(String requestedAddress) {
        if (requestedAddress == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String address = requestedAddress.trim();
        if (address.isEmpty() || address.length() > ADDRESS_MAX_LENGTH) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return address;
    }

    /** 선택 상세주소를 정규화하며 null 또는 공백은 저장값 없음으로 처리합니다. */
    public static String normalizeOptionalDetailAddress(String requestedDetailAddress) {
        if (requestedDetailAddress == null || requestedDetailAddress.isBlank()) {
            return null;
        }
        String detailAddress = requestedDetailAddress.trim();
        if (detailAddress.length() > DETAIL_ADDRESS_MAX_LENGTH) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return detailAddress;
    }
}
