package com.hansungteam.ersync.global.exception;

import com.hansungteam.ersync.global.logging.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 환자 정보가 로그에 섞이지 않도록 최소 정보만 남깁니다.
 */
public final class ApiErrorLogSupport {

    private static final String LOG_FORMAT =
            "event={} traceId={} code={} status={} method={} path={} message=\"{}\" exception={}";

    private ApiErrorLogSupport() {
    }

    /**
     * 요청 본문을 제외한 오류 메타데이터를 LogScope 호환 형식으로 기록합니다.
     *
     * @param logger 로그를 기록할 클래스의 로거
     * @param event 오류 분류
     * @param errorCode 표준 오류 코드
     * @param request 오류가 발생한 HTTP 요청
     * @param ex 원인 예외
     */
    public static void log(
            Logger logger,
            ApiErrorEvent event,
            ErrorCode errorCode,
            HttpServletRequest request,
            Exception ex
    ) {
        Object[] arguments = {
                event,
                TraceContext.currentTraceId(),
                errorCode.getCode(),
                errorCode.getStatus().value(),
                request.getMethod(),
                resolvePath(request),
                errorCode.getMessage(),
                ex.getClass().getSimpleName()
        };

        if (event == ApiErrorEvent.SYSTEM_ERROR) {
            logger.error(
                    LOG_FORMAT,
                    arguments[0],
                    arguments[1],
                    arguments[2],
                    arguments[3],
                    arguments[4],
                    arguments[5],
                    arguments[6],
                    arguments[7],
                    ex
            );
            return;
        }

        logger.warn(
                LOG_FORMAT,
                arguments
        );
    }

    private static String resolvePath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }
}
