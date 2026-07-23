package com.hansungteam.ersync.global.exception;

/**
 * 요청 필드 하나의 검증 실패 정보를 표현합니다.
 *
 * @param field 오류가 발생한 필드 경로
 * @param code 검증 오류 종류
 * @param message 클라이언트에 제공할 오류 메시지
 */
public record FieldErrorResponse(
        String field,
        String code,
        String message
) {
}
