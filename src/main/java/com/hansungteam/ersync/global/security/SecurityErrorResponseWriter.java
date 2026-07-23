package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 계층의 오류를 공통 API 오류 형식으로 직렬화합니다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 지정한 오류 코드로 HTTP 오류 응답을 작성합니다.
     *
     * @param response 작성 대상 응답
     * @param errorCode 응답할 공통 오류 코드
     * @throws IOException 응답 본문 직렬화 또는 출력에 실패한 경우
     */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.from(errorCode));
    }
}
