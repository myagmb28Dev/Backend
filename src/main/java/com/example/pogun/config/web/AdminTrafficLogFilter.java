package com.example.pogun.config.web;

import com.example.pogun.service.admin.AdminTrafficLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@RequiredArgsConstructor
public class AdminTrafficLogFilter extends OncePerRequestFilter {

    private static final int CACHE_LIMIT = 8192;
    private final AdminTrafficLogService adminTrafficLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/admin/traffic/logs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        ContentCachingResponseWrapper wrappedResponse = response instanceof ContentCachingResponseWrapper
                ? (ContentCachingResponseWrapper) response
                : new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String fullPath = wrappedRequest.getRequestURI() + (wrappedRequest.getQueryString() == null ? "" : "?" + wrappedRequest.getQueryString());
            adminTrafficLogService.recordInbound(
                    wrappedRequest.getMethod(),
                    fullPath,
                    wrappedResponse.getStatus(),
                    durationMs,
                    wrappedRequest.getRemoteAddr(),
                    extractBody(wrappedRequest.getContentAsByteArray(), wrappedRequest.getCharacterEncoding(), wrappedRequest.getContentType()),
                    extractBody(wrappedResponse.getContentAsByteArray(), wrappedResponse.getCharacterEncoding(), wrappedResponse.getContentType())
            );
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String extractBody(byte[] bytes, String encoding, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (!isTextContent(contentType)) {
            return "<non-text>";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (StringUtils.hasText(encoding)) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        String text = new String(bytes, charset).trim();
        int limit = 2000;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated)";
    }

    private boolean isTextContent(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String lower = contentType.toLowerCase();
        return lower.startsWith("application/json")
                || lower.startsWith("application/xml")
                || lower.startsWith("application/x-www-form-urlencoded")
                || lower.startsWith("text/");
    }
}

