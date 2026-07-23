package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.global.exception.ApiErrorEvent;
import com.hansungteam.ersync.global.exception.ApiErrorLogSupport;
import com.hansungteam.ersync.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 권한이 부족한 요청을 공통 오류 응답과 구조화 로그로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter responseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.AUTH_ROLE_REQUIRED;
        ApiErrorLogSupport.log(log, ApiErrorEvent.AUTH_ERROR, errorCode, request, accessDeniedException);
        responseWriter.write(response, errorCode);
    }
}
