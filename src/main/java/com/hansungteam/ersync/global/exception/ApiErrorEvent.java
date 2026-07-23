package com.hansungteam.ersync.global.exception;

/**
 * API 오류 로그를 원인 범주별로 분류합니다.
 */
public enum ApiErrorEvent {
    BUSINESS_ERROR,
    VALIDATION_ERROR,
    AUTH_ERROR,
    SYSTEM_ERROR
}
