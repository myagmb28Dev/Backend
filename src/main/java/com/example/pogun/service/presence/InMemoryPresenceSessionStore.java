package com.example.pogun.service.presence;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPresenceSessionStore implements PresenceSessionStore {

    private final ConcurrentHashMap<String, ConcurrentMap<String, Instant>> activeWebSocketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastTouchedAtCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> forcedOfflineAtCache = new ConcurrentHashMap<>();

    @Override
    public void putSession(String firebaseUid, String sessionId, Instant touchedAt) {
        activeWebSocketSessions
                .computeIfAbsent(firebaseUid, ignored -> new ConcurrentHashMap<>())
                .put(sessionId, touchedAt);
    }

    @Override
    public void removeSession(String firebaseUid, String sessionId) {
        activeWebSocketSessions.computeIfPresent(firebaseUid, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    public void clearSessions(String firebaseUid) {
        activeWebSocketSessions.remove(firebaseUid);
    }

    @Override
    public Map<String, Instant> getSessions(String firebaseUid) {
        ConcurrentMap<String, Instant> sessions = activeWebSocketSessions.get(firebaseUid);
        if (sessions == null || sessions.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(sessions);
    }

    @Override
    public Set<String> findUsersWithSessions() {
        return new HashSet<>(activeWebSocketSessions.keySet());
    }

    @Override
    public Instant getLastTouchedAt(String firebaseUid) {
        return lastTouchedAtCache.get(firebaseUid);
    }

    @Override
    public void setLastTouchedAt(String firebaseUid, Instant touchedAt) {
        lastTouchedAtCache.put(firebaseUid, touchedAt);
    }

    @Override
    public void clearLastTouchedAt(String firebaseUid) {
        lastTouchedAtCache.remove(firebaseUid);
    }

    @Override
    public Instant getForcedOfflineAt(String firebaseUid) {
        return forcedOfflineAtCache.get(firebaseUid);
    }

    @Override
    public void setForcedOfflineAt(String firebaseUid, Instant forcedOfflineAt) {
        forcedOfflineAtCache.put(firebaseUid, forcedOfflineAt);
    }

    @Override
    public void clearForcedOfflineAt(String firebaseUid) {
        forcedOfflineAtCache.remove(firebaseUid);
    }
}
