package com.example.pogun.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로컬 검증 환경에서만 커뮤니티 API의 5xx 응답을 강제로 재현하는 필터이다.
 */
@Component
@RequiredArgsConstructor
public class CommunityTestFailureFilter extends OncePerRequestFilter {

    static final String FORCE_HEADER = "X-Community-Test-Force-5xx";

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${app.community.test-force-5xx-enabled:false}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }

        if (request.getHeader(FORCE_HEADER) == null) {
            return true;
        }

        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/community/posts");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        apiErrorResponseWriter.write(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "로컬 커뮤니티 5xx 검증용 강제 오류입니다.",
                null
        );
    }
}
