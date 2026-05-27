package com.example.pogun.config.web;

import com.example.pogun.service.admin.AdminTrafficLogService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;
import org.zalando.logbook.servlet.LogbookFilter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AdminTrafficLogbookConfig {

    @Bean
    public Logbook adminTrafficLogbook(Sink adminTrafficSink) {
        return Logbook.builder()
                .sink(adminTrafficSink)
                .build();
    }

    @Bean
    public Sink adminTrafficSink(AdminTrafficLogService adminTrafficLogService) {
        return new AdminTrafficSink(adminTrafficLogService);
    }

    @Bean
    public FilterRegistrationBean<Filter> adminTrafficLogbookFilter(Logbook logbook) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LogbookFilter(logbook));
        bean.setName("adminTrafficLogbookFilter");
        bean.addUrlPatterns("/*");
        bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    private static final class AdminTrafficSink implements Sink {

        private static final int BODY_LIMIT = 2000;
        private final AdminTrafficLogService adminTrafficLogService;
        private final Map<String, Long> startedAtByCorrelationId = new ConcurrentHashMap<>();

        private AdminTrafficSink(AdminTrafficLogService adminTrafficLogService) {
            this.adminTrafficLogService = adminTrafficLogService;
        }

        @Override
        public void write(Precorrelation precorrelation, HttpRequest request) {
            startedAtByCorrelationId.put(precorrelation.getId(), System.currentTimeMillis());
        }

        @Override
        public void write(Correlation correlation, HttpRequest request, HttpResponse response) {
            String path = buildPath(request);
            if (shouldSkip(path)) {
                startedAtByCorrelationId.remove(correlation.getId());
                return;
            }

            long startedAt = startedAtByCorrelationId.getOrDefault(correlation.getId(), System.currentTimeMillis());
            startedAtByCorrelationId.remove(correlation.getId());
            long durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);

            adminTrafficLogService.recordInbound(
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    durationMs,
                    safe(request.getRemote()),
                    extractBody(readBody(request), request.getContentType()),
                    extractBody(readBody(response), response.getContentType())
            );
        }

        private String readBody(HttpRequest request) {
            try {
                return request.getBodyAsString();
            } catch (Exception ignored) {
                return "";
            }
        }

        private String readBody(HttpResponse response) {
            try {
                return response.getBodyAsString();
            } catch (Exception ignored) {
                return "";
            }
        }

        private String buildPath(HttpRequest request) {
            String path = request.getPath();
            String query = request.getQuery();
            if (!StringUtils.hasText(query)) {
                return safe(path);
            }
            return safe(path) + "?" + query;
        }

        private String extractBody(String body, String contentType) {
            if (body == null || body.isBlank()) {
                return "";
            }
            if (!isTextContent(contentType)) {
                return "<non-text>";
            }
            String normalized = new String(body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).trim();
            if (normalized.length() <= BODY_LIMIT) {
                return normalized;
            }
            return normalized.substring(0, BODY_LIMIT) + "...(truncated)";
        }

        private boolean isTextContent(String contentType) {
            if (!StringUtils.hasText(contentType)) {
                return true;
            }
            String lower = contentType.toLowerCase(Locale.ROOT);
            return lower.startsWith("application/json")
                    || lower.startsWith("application/xml")
                    || lower.startsWith("application/x-www-form-urlencoded")
                    || lower.startsWith("text/");
        }

        private boolean shouldSkip(String path) {
            if (!StringUtils.hasText(path)) {
                return true;
            }
            if (AdminTrafficLogService.isExcludedTrafficPath(path)) {
                return true;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            return "/".equals(lower)
                    || "/favicon.ico".equals(lower)
                    || lower.startsWith("/full_compact/")
                    || lower.equals("/full_compact.html")
                    || lower.startsWith("/js/")
                    || lower.startsWith("/css/")
                    || lower.startsWith("/images/")
                    || lower.startsWith("/img/")
                    || lower.startsWith("/assets/")
                    || lower.endsWith(".html")
                    || lower.endsWith(".css")
                    || lower.endsWith(".js")
                    || lower.endsWith(".mjs")
                    || lower.endsWith(".map")
                    || lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif")
                    || lower.endsWith(".svg")
                    || lower.endsWith(".ico")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".woff")
                    || lower.endsWith(".woff2");
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
