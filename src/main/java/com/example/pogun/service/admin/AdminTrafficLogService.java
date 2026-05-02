package com.example.pogun.service.admin;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Service
public class AdminTrafficLogService {

    private static final int MAX_LOG_SIZE = 500;
    private static final List<String> TRACKED_API_PREFIXES = List.of(
            "/api/missing-pets",
            "/api/shelter",
            "/api/reports",
            "/api/admin",
            "/api/community"
    );

    private final Deque<Map<String, Object>> logs = new ArrayDeque<>();
    private final Object lock = new Object();

    public void recordInbound(String method, String path, int status, long durationMs, String remoteAddr) {
        push(Map.of(
                "direction", "IN",
                "timestamp", Instant.now().toString(),
                "method", method,
                "path", path,
                "status", status,
                "durationMs", durationMs,
                "remoteAddr", remoteAddr == null ? "" : remoteAddr
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
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        synchronized (lock) {
            List<Map<String, Object>> snapshot = new ArrayList<>(logs);
            int from = Math.max(0, snapshot.size() - resolvedLimit);
            return snapshot.subList(from, snapshot.size());
        }
    }

    public List<String> trackedApiPrefixes() {
        return TRACKED_API_PREFIXES;
    }

    private void push(Map<String, Object> entry) {
        synchronized (lock) {
            logs.addLast(entry);
            while (logs.size() > MAX_LOG_SIZE) {
                logs.removeFirst();
            }
        }
    }
}
