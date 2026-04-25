package com.example.pogun.service.presence;

import com.example.pogun.entity.user.enums.UserAvailabilityStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPresenceSessionStore implements PresenceSessionStore {

    private final ConcurrentHashMap<String, ConcurrentMap<String, Instant>> activeGlobalSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentMap<String, Instant>> activeWebSocketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastTouchedAtCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> forcedOfflineAtCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> disconnectGraceUntilCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserAvailabilityStatus> manualPresenceStatusCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserAvailabilityStatus> effectivePresenceStatusCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> connectionStateCache = new ConcurrentHashMap<>();

    @Override
    public void putGlobalSession(String firebaseUid, String clientSessionId, Instant touchedAt) {
        activeGlobalSessions
                .computeIfAbsent(firebaseUid, ignored -> new ConcurrentHashMap<>())
                .put(clientSessionId, touchedAt);
    }

    @Override
    public void removeGlobalSession(String firebaseUid, String clientSessionId) {
        activeGlobalSessions.computeIfPresent(firebaseUid, (ignored, sessions) -> {
            sessions.remove(clientSessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    public void clearGlobalSessions(String firebaseUid) {
        activeGlobalSessions.remove(firebaseUid);
    }

    @Override
    public Map<String, Instant> getGlobalSessions(String firebaseUid) {
        ConcurrentMap<String, Instant> sessions = activeGlobalSessions.get(firebaseUid);
        if (sessions == null || sessions.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(sessions);
    }

    @Override
    public Set<String> findUsersWithGlobalSessions() {
        return new HashSet<>(activeGlobalSessions.keySet());
    }

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

    @Override
    public Instant getDisconnectGraceUntil(String firebaseUid) {
        return disconnectGraceUntilCache.get(firebaseUid);
    }

    @Override
    public void setDisconnectGraceUntil(String firebaseUid, Instant disconnectGraceUntil) {
        disconnectGraceUntilCache.put(firebaseUid, disconnectGraceUntil);
    }

    @Override
    public void clearDisconnectGraceUntil(String firebaseUid) {
        disconnectGraceUntilCache.remove(firebaseUid);
    }

    @Override
    public Set<String> findUsersWithDisconnectGrace() {
        return new HashSet<>(disconnectGraceUntilCache.keySet());
    }

    @Override
    public UserAvailabilityStatus getManualPresenceStatus(String firebaseUid) {
        return manualPresenceStatusCache.get(firebaseUid);
    }

    @Override
    public void setManualPresenceStatus(String firebaseUid, UserAvailabilityStatus manualPresenceStatus) {
        if (manualPresenceStatus != null) {
            manualPresenceStatusCache.put(firebaseUid, manualPresenceStatus);
        }
    }

    @Override
    public void clearManualPresenceStatus(String firebaseUid) {
        manualPresenceStatusCache.remove(firebaseUid);
    }

    @Override
    public UserAvailabilityStatus getEffectivePresenceStatus(String firebaseUid) {
        return effectivePresenceStatusCache.get(firebaseUid);
    }

    @Override
    public void setEffectivePresenceStatus(String firebaseUid, UserAvailabilityStatus effectivePresenceStatus) {
        if (effectivePresenceStatus != null) {
            effectivePresenceStatusCache.put(firebaseUid, effectivePresenceStatus);
        }
    }

    @Override
    public void clearEffectivePresenceStatus(String firebaseUid) {
        effectivePresenceStatusCache.remove(firebaseUid);
    }

    @Override
    public String getConnectionState(String firebaseUid) {
        return connectionStateCache.get(firebaseUid);
    }

    @Override
    public void setConnectionState(String firebaseUid, String connectionState) {
        if (connectionState != null && !connectionState.isBlank()) {
            connectionStateCache.put(firebaseUid, connectionState);
        }
    }

    @Override
    public void clearConnectionState(String firebaseUid) {
        connectionStateCache.remove(firebaseUid);
    }
}
