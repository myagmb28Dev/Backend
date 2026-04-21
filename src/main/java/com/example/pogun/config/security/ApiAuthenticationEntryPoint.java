package com.example.pogun.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
/**
 * 애플리케이션 설정을 담당하는 ApiAuthenticationEntryPoint이다.
 */

@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        // 인증 정보가 전혀 없는 요청은 여기서 공통 401 JSON 으로 변환된다.
        apiErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.", null);
    }
}

