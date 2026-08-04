package com.hansungteam.ersync.hospital.search.application;

/** 잠시 뒤 제한된 횟수로 재시도할 수 있는 지도 API 오류입니다. */
public class TemporaryRouteEstimateException extends RuntimeException {

    public TemporaryRouteEstimateException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemporaryRouteEstimateException(String message) {
        super(message);
    }
}
