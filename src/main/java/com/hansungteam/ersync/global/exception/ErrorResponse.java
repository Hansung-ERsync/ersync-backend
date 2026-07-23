package com.hansungteam.ersync.global.exception;

import com.hansungteam.ersync.global.logging.TraceContext;

import java.util.List;

/**
 * 실패 API의 표준 응답입니다.
 *
 * @param code 프론트 분기용 오류 코드
 * @param message 사용자에게 보여줄 수 있는 오류 메시지
 * @param fieldErrors 필드별 검증 오류
 * @param traceId 서버 로그 추적 ID
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        String traceId
) {

    public ErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * 필드 오류가 없는 표준 오류 응답을 생성합니다.
     *
     * @param errorCode 응답할 오류 코드
     * @return 현재 요청의 추적 ID가 포함된 오류 응답
     */
    public static ErrorResponse from(ErrorCode errorCode) {
        return from(errorCode, List.of());
    }

    /**
     * 필드별 검증 결과가 포함된 표준 오류 응답을 생성합니다.
     *
     * @param errorCode 응답할 오류 코드
     * @param fieldErrors 필드별 검증 오류
     * @return 현재 요청의 추적 ID가 포함된 오류 응답
     */
    public static ErrorResponse from(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                fieldErrors,
                TraceContext.currentTraceId()
        );
    }
}
