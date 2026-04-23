package com.example.pogun.config;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.service.ai.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * AI 소비 대상 업로드 이미지는 AI 서버만 접근할 수 있게 API 키로 한 번 더 검증한다.
 */
@Component
@RequiredArgsConstructor
public class AiProtectedUploadAccessInterceptor implements HandlerInterceptor {

    private static final String MISSING_PET_UPLOAD_PREFIX = "/uploads/missing-pets/";
    private static final String SHELTER_UPLOAD_PREFIX = "/uploads/shelter/";
    private static final String AI_API_KEY_HEADER = "X-AI-API-KEY";

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith(MISSING_PET_UPLOAD_PREFIX) && !path.startsWith(SHELTER_UPLOAD_PREFIX)) {
            return true;
        }

        try {
            aiService.verifyAiApiKey(request.getHeader(AI_API_KEY_HEADER));
            return true;
        } catch (ApiException e) {
            response.setStatus(e.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.fail(e.getStatus(), e.getCode(), e.getMessage(), e.getDetail())
            );
            return false;
        }
    }
}
