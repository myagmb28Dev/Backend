package com.example.pogun.config.web;

import com.example.pogun.service.admin.AdminTrafficLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class AdminTrafficLogFilter extends OncePerRequestFilter {

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
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String fullPath = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            adminTrafficLogService.recordInbound(
                    request.getMethod(),
                    fullPath,
                    response.getStatus(),
                    durationMs,
                    request.getRemoteAddr()
            );
        }
    }
}

