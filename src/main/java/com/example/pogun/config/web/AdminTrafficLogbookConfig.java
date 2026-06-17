package com.example.pogun.config.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
public class AdminTrafficLogbookConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "access_token",
            "refresh_token",
            "id_token",
            "token",
            "password",
            "firebaseidtoken",
            "firebase_id_token",
            "refreshtoken",
            "refreshToken",
            "idToken",
            "accessToken",
            "client_secret",
            "secret",
            "api_key",
            "apikey",
            "x-ai-api-key"
    );

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
            String path = sanitizePath(buildPath(request));
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
            String normalized = sanitizeBody(body, contentType).trim();
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

    static String sanitizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String trimmed = path.trim();
        try {
            URI uri = URI.create(trimmed);
            String rawQuery = uri.getRawQuery();
            if (!StringUtils.hasText(rawQuery)) {
                return trimmed;
            }
            String sanitizedQuery = sanitizeFormEncoded(rawQuery, true);
            if (uri.getScheme() != null) {
                return new URI(
                        uri.getScheme(),
                        uri.getAuthority(),
                        uri.getPath(),
                        sanitizedQuery,
                        uri.getFragment()
                ).toString();
            }
            return (uri.getPath() == null ? "" : uri.getPath())
                    + "?"
                    + sanitizedQuery
                    + (uri.getFragment() == null ? "" : "#" + uri.getFragment());
        } catch (Exception ignored) {
            int queryIndex = trimmed.indexOf('?');
            if (queryIndex < 0) {
                return trimmed;
            }
            String base = trimmed.substring(0, queryIndex);
            String query = trimmed.substring(queryIndex + 1);
            return base + "?" + sanitizeFormEncoded(query, true);
        }
    }

    static String sanitizeBody(String body, String contentType) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String normalizedBody = new String(body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (normalizedContentType.startsWith("application/json")) {
            return sanitizeJson(normalizedBody);
        }
        if (normalizedContentType.startsWith("application/x-www-form-urlencoded")) {
            return sanitizeFormEncoded(normalizedBody, false);
        }
        return redactPlainText(normalizedBody);
    }

    private static String sanitizeJson(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            sanitizeJsonNode(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ignored) {
            return redactPlainText(body);
        }
    }

    private static void sanitizeJsonNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = objectNode.get(fieldName);
                if (isSensitiveKey(fieldName)) {
                    objectNode.put(fieldName, REDACTED_VALUE);
                    return;
                }
                sanitizeJsonNode(child);
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                sanitizeJsonNode(child);
            }
        }
    }

    private static String sanitizeFormEncoded(String raw, boolean keepEncoding) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String[] pairs = raw.split("&");
        return java.util.Arrays.stream(pairs)
                .map(pair -> sanitizeFormPair(pair, keepEncoding))
                .collect(Collectors.joining("&"));
    }

    private static String sanitizeFormPair(String pair, boolean keepEncoding) {
        int separator = pair.indexOf('=');
        String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
        String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
        String decodedKey = decode(rawKey, keepEncoding);
        if (!isSensitiveKey(decodedKey)) {
            return pair;
        }
        String encodedValue = keepEncoding ? encode(REDACTED_VALUE) : REDACTED_VALUE;
        return rawKey + "=" + encodedValue;
    }

    private static String redactPlainText(String body) {
        String redacted = body;
        for (String key : SENSITIVE_KEYS) {
            redacted = redacted.replaceAll(
                    "(?i)(\\b" + java.util.regex.Pattern.quote(key) + "\\b\\s*[=:]\\s*[\"']?)([^\\s,\"'&}]+)",
                    "$1" + REDACTED_VALUE
            );
        }
        return redacted;
    }

    private static boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(key) || SENSITIVE_KEYS.contains(normalized);
    }

    private static String decode(String value, boolean keepEncoding) {
        if (!keepEncoding) {
            return value;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
