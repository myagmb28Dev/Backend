package com.example.pogun.config.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Locale;

public final class RequestHostResolver {

    private RequestHostResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown-host";
        }

        String origin = trimToNull(request.getHeader("Origin"));
        if (origin != null) {
            String originHost = hostFromOrigin(origin);
            if (originHost != null) {
                return originHost;
            }
        }

        String forwardedHost = firstHostHeader(request.getHeader("X-Forwarded-Host"));
        if (forwardedHost != null) {
            return stripPort(forwardedHost);
        }

        String hostHeader = firstHostHeader(request.getHeader("Host"));
        if (hostHeader != null) {
            return stripPort(hostHeader);
        }

        String serverName = trimToNull(request.getServerName());
        return serverName != null ? serverName.toLowerCase(Locale.ROOT) : "unknown-host";
    }

    private static String hostFromOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstHostHeader(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return null;
        }
        int commaIndex = raw.indexOf(',');
        String first = commaIndex >= 0 ? raw.substring(0, commaIndex) : raw;
        return trimToNull(first);
    }

    private static String stripPort(String hostValue) {
        String normalized = hostValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[")) {
            int end = normalized.indexOf(']');
            if (end > 0) {
                return normalized.substring(1, end);
            }
        }
        int colon = normalized.indexOf(':');
        return colon > 0 ? normalized.substring(0, colon) : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
