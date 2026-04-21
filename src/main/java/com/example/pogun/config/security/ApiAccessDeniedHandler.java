package com.example.pogun.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
/**
 * 애플리케이션 설정을 담당하는 ApiAccessDeniedHandler이다.
 */

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        // 인증은 되었지만 role 이 맞지 않는 요청은 403 JSON 으로 통일한다.
        apiErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다.", null);
    }
}

