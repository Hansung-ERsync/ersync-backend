package com.hansungteam.ersync.global.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 표준 오류 코드를 담아 던지는 런타임 예외입니다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 기능 코드에서 처리할 표준 오류를 생성합니다.
     *
     * @param errorCode 발생 조건에 대응하는 오류 코드
     */
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
