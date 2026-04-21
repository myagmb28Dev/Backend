package com.example.pogun.config;

import com.example.pogun.dto.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
/**
 * 애플리케이션 설정을 담당하는 ApiErrorResponseWriter이다.
 */

@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String code, String message, Object detail) throws IOException {
        // 필터나 시큐리티 계층에서도 컨트롤러와 동일한 ApiResponse 포맷을 재사용하기 위한 공용 writer 다.
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(status, code, message, detail));
    }
}

