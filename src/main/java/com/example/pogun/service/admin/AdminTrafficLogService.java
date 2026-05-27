package com.example.pogun.service.admin;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminTrafficLogService {

    private static final int MAX_LOG_SIZE = 500;
    private static final List<String> TRACKED_API_PREFIXES = List.of(
            "/api/"
    );

    private final Deque<Map<String, Object>> logs = new ArrayDeque<>();
    private final Object lock = new Object();

    public void recordInbound(String method, String path, int status, long durationMs, String remoteAddr, String requestBody, String responseBody) {
        if (isExcludedTrafficPath(path)) {
            return;
        }
        push(Map.of(
                "direction", "IN",
                "timestamp", Instant.now().toString(),
                "method", method,
                "path", path,
                "status", status,
                "durationMs", durationMs,
                "remoteAddr", remoteAddr == null ? "" : remoteAddr,
                "requestBody", requestBody == null ? "" : requestBody,
                "responseBody", responseBody == null ? "" : responseBody
        ));
    }

    public void recordOutbound(String method, String url, int status, long durationMs, String source) {
        push(Map.of(
                "direction", "OUT",
                "timestamp", Instant.now().toString(),
                "method", method,
                "path", url,
                "status", status,
                "durationMs", durationMs,
                "remoteAddr", source == null ? "" : source
        ));
    }

    public List<Map<String, Object>> recent(int limit) {
        return recent(limit, false);
    }

    public List<Map<String, Object>> recent(int limit, boolean errorsOnly) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        synchronized (lock) {
            List<Map<String, Object>> snapshot = new ArrayList<>(logs).stream()
                    .filter(entry -> !isExcludedTrafficPath(String.valueOf(entry.get("path"))))
                    .toList();
            if (errorsOnly) {
                snapshot = snapshot.stream()
                        .filter(this::isUnexpectedStatus)
                        .toList();
            }
            int from = Math.max(0, snapshot.size() - resolvedLimit);
            return snapshot.subList(from, snapshot.size());
        }
    }

    public List<String> trackedApiPrefixes() {
        return TRACKED_API_PREFIXES;
    }

    public int currentErrorCount() {
        synchronized (lock) {
            return (int) logs.stream()
                    .filter(entry -> !isExcludedTrafficPath(String.valueOf(entry.get("path"))))
                    .filter(this::isUnexpectedStatus)
                    .count();
        }
    }

    private void push(Map<String, Object> entry) {
        synchronized (lock) {
            logs.addLast(entry);
            while (logs.size() > MAX_LOG_SIZE) {
                logs.removeFirst();
            }
        }
    }

    private boolean isUnexpectedStatus(Map<String, Object> entry) {
        Object value = entry.get("status");
        if (value instanceof Number number) {
            int status = number.intValue();
            // Non-errors: 1xx/2xx/3xx (100-399).
            // Errors: network/external failures represented as 0, or 4xx/5xx (>=400).
            return status == 0 || status >= 400;
        }
        if (value instanceof String text) {
            try {
                int status = Integer.parseInt(text);
                return status == 0 || status >= 400;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return true;
    }

    public static boolean isExcludedTrafficPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return true;
        }
        String normalized = normalizePath(rawPath);
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.startsWith("/api/admin/traffic/");
    }

    private static String normalizePath(String rawPath) {
        String value = rawPath.trim();
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() != null && uri.getPath() != null) {
                value = uri.getPath();
            }
        } catch (RuntimeException ignored) {
            // Keep the raw input when URI parsing fails.
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0) {
            value = value.substring(0, hashIndex);
        }
        return value;
    }
}
