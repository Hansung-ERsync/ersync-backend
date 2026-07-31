package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.global.exception.ApiErrorEvent;
import com.hansungteam.ersync.global.exception.ApiErrorLogSupport;
import com.hansungteam.ersync.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;

import java.io.IOException;

/**
 * 인증되지 않은 요청을 공통 오류 응답과 구조화 로그로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        ErrorCode errorCode = authorization != null && authorization.startsWith("Bearer ")
                ? ErrorCode.AUTH_ACCESS_TOKEN_INVALID
                : ErrorCode.AUTH_AUTHENTICATION_REQUIRED;
        ApiErrorLogSupport.log(log, ApiErrorEvent.AUTH_ERROR, errorCode, request, authException);
        responseWriter.write(response, errorCode);
    }
}
