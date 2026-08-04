package com.hansungteam.ersync.hospital.search.application;

/** 재시도 없이 ETA만 UNAVAILABLE로 종료할 지도 설정·응답 오류입니다. */
public class PermanentRouteEstimateException extends RuntimeException {

    public PermanentRouteEstimateException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermanentRouteEstimateException(String message) {
        super(message);
    }
}
