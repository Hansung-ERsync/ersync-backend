package com.hansungteam.ersync.global.logging;

import org.slf4j.MDC;

/**
 * 요청 추적 ID의 헤더 및 MDC 규칙을 제공합니다.
 */
public final class TraceContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final String UNKNOWN_TRACE_ID = "unknown";

    private TraceContext() {
    }

    /**
     * 현재 요청의 추적 ID를 반환합니다.
     *
     * @return MDC에 저장된 추적 ID. 요청 문맥이 없으면 {@code unknown}
     */
    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? UNKNOWN_TRACE_ID : traceId;
    }
}
