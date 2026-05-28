package com.example.pogun.service.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.presence.PresenceSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPresenceService {

    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(15);
    // Realtime-priority: faster offline detection.
    private static final Duration GLOBAL_SESSION_STALE_AFTER = Duration.ofSeconds(10);
    // DM/socket freshness stays shorter than global presence.
    private static final Duration WEBSOCKET_SESSION_STALE_AFTER = Duration.ofSeconds(8);
    // WebSocket preSend 훅에서 프레임마다 전체 presence cache를 갱신하면 DM 전송 경로가 느려질 수 있어 완만하게 스로틀한다.
    private static final Duration WEBSOCKET_REFRESH_THROTTLE = Duration.ofSeconds(2);
    private static final String AUTH_GLOBAL_SESSION_ID = "auth";
    private static final String CONNECTION_CONNECTED = "connected";
    private static final String CONNECTION_DISCONNECTED = "disconnected";

    private final UserRepository userRepository;
    private final PresenceSessionStore presenceSessionStore;
    private final Map<String, Instant> websocketRefreshGate = new ConcurrentHashMap<>();

    @Transactional
    public boolean touch(String firebaseUid) {
        return touchInternal(firebaseUid, false);
    }

    @Transactional
    public boolean touchFromAuthentication(String firebaseUid) {
        rememberGlobalAuthenticationSession(firebaseUid, null);
        return touchInternal(firebaseUid, true);
    }

    @Transactional
    public boolean touchFromAuthentication(String firebaseUid, String sessionScopeHost) {
        rememberGlobalAuthenticationSession(firebaseUid, sessionScopeHost);
        return touchInternal(firebaseUid, true);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public boolean touchFromAuthenticationSafely(String firebaseUid) {
        try {
            return touchFromAuthentication(firebaseUid);
        } catch (RuntimeException e) {
            log.warn("[presence] auth touch skipped uid={} reason={}", firebaseUid, e.getClass().getSimpleName());
            return false;
        }
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public boolean touchFromAuthenticationSafely(String firebaseUid, String sessionScopeHost) {
        try {
            return touchFromAuthentication(firebaseUid, sessionScopeHost);
        } catch (RuntimeException e) {
            log.warn("[presence] auth touch skipped uid={} scope={} reason={}",
                    firebaseUid, sessionScopeHost, e.getClass().getSimpleName());
            return false;
        }
    }

    @Transactional
    public PresenceSnapshot heartbeat(String firebaseUid, String clientSessionId) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return new PresenceSnapshot(UserAvailabilityStatus.ONLINE, UserAvailabilityStatus.OFFLINE, CONNECTION_DISCONNECTED, null);
        }
        String sessionId = normalizeClientSessionId(clientSessionId);
        Instant now = Instant.now();
        presenceSessionStore.putGlobalSession(firebaseUid, sessionId, now);
        presenceSessionStore.clearForcedOfflineAt(firebaseUid);
        presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
        touchInternal(firebaseUid, true);
        UserAvailabilityStatus manualStatus = presenceSessionStore.getManualPresenceStatus(firebaseUid);
        syncPresenceCache(firebaseUid, manualStatus);
        return userRepository.findByFirebaseUid(firebaseUid)
                .map(this::snapshot)
                .orElseGet(() -> buildSnapshot(firebaseUid, manualStatus != null ? manualStatus : UserAvailabilityStatus.ONLINE, now));
    }

    private boolean touchInternal(String firebaseUid, boolean allowForcedOfflineRecovery) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        Instant lastTouched = presenceSessionStore.getLastTouchedAt(firebaseUid);
        if (lastTouched != null && Duration.between(lastTouched, now).compareTo(TOUCH_THROTTLE) < 0) {
            return false;
        }
        boolean blockedByForcedOffline = isForcedOfflineWithoutTrackedSession(firebaseUid) && !allowForcedOfflineRecovery;
        if (blockedByForcedOffline) {
            return false;
        }
        if (userRepository.updateLastActiveAtByFirebaseUid(firebaseUid, now) > 0) {
            presenceSessionStore.setLastTouchedAt(firebaseUid, now);
            presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
            if (allowForcedOfflineRecovery || !presenceSessionStore.getSessions(firebaseUid).isEmpty()) {
                presenceSessionStore.clearForcedOfflineAt(firebaseUid);
            }
            syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
            return true;
        }
        return false;
    }

    @Transactional
    public void forceOffline(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        presenceSessionStore.clearGlobalSessions(firebaseUid);
        presenceSessionStore.clearSessions(firebaseUid);
        presenceSessionStore.clearLastTouchedAt(firebaseUid);
        presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
        presenceSessionStore.setForcedOfflineAt(firebaseUid, now);
        userRepository.updateLastActiveAtByFirebaseUid(firebaseUid, now);
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
        log.info("[presence] forced offline uid={} at={} connectionState={}", firebaseUid, now, CONNECTION_DISCONNECTED);
    }

    @Transactional
    public void markWebSocketConnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        log.info("[presence] websocket connected uid={} sessionId={}", firebaseUid, sessionId);
        rememberGlobalAuthenticationSession(firebaseUid, null);
        rememberWebSocketActivity(firebaseUid, sessionId, Instant.now());
        touch(firebaseUid);
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    public void refreshWebSocketSession(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        presenceSessionStore.putSession(firebaseUid, sessionId, now);

        String refreshKey = websocketRefreshKey(firebaseUid, sessionId);
        Instant lastRefreshAt = websocketRefreshGate.get(refreshKey);
        if (lastRefreshAt != null && Duration.between(lastRefreshAt, now).compareTo(WEBSOCKET_REFRESH_THROTTLE) < 0) {
            return;
        }
        websocketRefreshGate.put(refreshKey, now);

        if (presenceSessionStore.getForcedOfflineAt(firebaseUid) != null) {
            log.debug("[presence] skip refresh for forced-offline uid={} sessionId={}", firebaseUid, sessionId);
            return;
        }
        rememberGlobalAuthenticationSession(firebaseUid, null);
        presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    @Transactional
    public void markWebSocketDisconnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            presenceSessionStore.removeSession(firebaseUid, sessionId);
            websocketRefreshGate.remove(websocketRefreshKey(firebaseUid, sessionId));
        }
        Instant now = Instant.now();
        if (hasActiveWebSocketSession(firebaseUid)) {
            presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
            touch(firebaseUid);
            log.info("[presence] websocket disconnected but still active uid={} sessionId={}", firebaseUid, sessionId);
        } else {
            presenceSessionStore.setLastTouchedAt(firebaseUid, now);
            log.info("[presence] websocket disconnected uid={} sessionId={} globalConnectionState={}", firebaseUid, sessionId, resolveActualConnectionState(firebaseUid));
        }
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    @Transactional(readOnly = true)
    public PresenceSnapshot snapshot(User user) {
        if (user == null || user.getFirebaseUid() == null) {
            return new PresenceSnapshot(UserAvailabilityStatus.ONLINE, UserAvailabilityStatus.OFFLINE, CONNECTION_DISCONNECTED, null);
        }
        try {
            UserAvailabilityStatus manualStatus = resolveManualPresenceStatus(user.getFirebaseUid(), user.getAvailabilityStatus());
            PresenceSnapshot snapshot = buildSnapshot(user.getFirebaseUid(), manualStatus, user.getLastActiveAt());
            log.debug("[presence] snapshot uid={} manual={} connectionState={} effective={}",
                    user.getFirebaseUid(),
                    snapshot.manualPresenceStatus(),
                    snapshot.actualConnectionState(),
                    snapshot.availabilityStatus());
            return snapshot;
        } catch (RuntimeException e) {
            // Redis 장애 시에도 API/채팅 핵심 플로우가 500으로 중단되지 않도록 안전 폴백한다.
            UserAvailabilityStatus manualFallback = user.getAvailabilityStatus() != null
                    ? user.getAvailabilityStatus()
                    : UserAvailabilityStatus.ONLINE;
            log.warn("[presence] snapshot fallback uid={} reason={}", user.getFirebaseUid(), e.getClass().getSimpleName());
            return new PresenceSnapshot(
                    manualFallback,
                    UserAvailabilityStatus.OFFLINE,
                    CONNECTION_DISCONNECTED,
                    user.getLastActiveAt()
            );
        }
    }

    @Transactional
    public void applyManualPresenceStatus(String firebaseUid, UserAvailabilityStatus manualPresenceStatus) {
        if (firebaseUid == null || firebaseUid.isBlank() || manualPresenceStatus == null) {
            return;
        }
        presenceSessionStore.setManualPresenceStatus(firebaseUid, manualPresenceStatus);
        syncPresenceCache(firebaseUid, manualPresenceStatus);
        log.info("[presence] manual status updated uid={} manual={} connectionState={} effective={}",
                firebaseUid,
                manualPresenceStatus,
                resolveActualConnectionState(firebaseUid),
                resolveEffectivePresenceStatus(resolveIsConnected(firebaseUid), manualPresenceStatus));
    }

    @Transactional
    public Set<String> reconcileStaleSessions() {
        Instant now = Instant.now();
        Set<String> changedFirebaseUids = new LinkedHashSet<>();
        for (String firebaseUid : presenceSessionStore.findUsersWithGlobalSessions()) {
            boolean wasConnected = !presenceSessionStore.getGlobalSessions(firebaseUid).isEmpty();
            pruneStaleGlobalSessions(firebaseUid, now);
            boolean connected = !presenceSessionStore.getGlobalSessions(firebaseUid).isEmpty();
            if (wasConnected && !connected && presenceSessionStore.getForcedOfflineAt(firebaseUid) == null) {
                presenceSessionStore.setConnectionState(firebaseUid, CONNECTION_DISCONNECTED);
                presenceSessionStore.setEffectivePresenceStatus(firebaseUid, UserAvailabilityStatus.OFFLINE);
                changedFirebaseUids.add(firebaseUid);
                log.info("[presence] stale global session pruned uid={} -> disconnected", firebaseUid);
            }
        }
        for (String firebaseUid : presenceSessionStore.findUsersWithSessions()) {
            pruneStaleWebSocketSessions(firebaseUid, now);
        }
        return changedFirebaseUids;
    }

    public UserAvailabilityStatus resolveEffectivePresenceStatus(boolean connected, UserAvailabilityStatus manualPresenceStatus) {
        UserAvailabilityStatus manual = manualPresenceStatus != null ? manualPresenceStatus : UserAvailabilityStatus.ONLINE;
        if (!connected) {
            return UserAvailabilityStatus.OFFLINE;
        }
        if (manual == UserAvailabilityStatus.OFFLINE) {
            return UserAvailabilityStatus.OFFLINE;
        }
        if (manual == UserAvailabilityStatus.IDLE) {
            return UserAvailabilityStatus.IDLE;
        }
        return UserAvailabilityStatus.ONLINE;
    }

    private boolean hasActiveWebSocketSession(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return false;
        }
        pruneStaleWebSocketSessions(firebaseUid, Instant.now());
        return !presenceSessionStore.getSessions(firebaseUid).isEmpty();
    }

    private PresenceSnapshot buildSnapshot(String firebaseUid, UserAvailabilityStatus manualStatus, Instant persistedLastActiveAt) {
        Instant now = Instant.now();
        Instant lastActiveAt = resolveLastActiveAt(firebaseUid, persistedLastActiveAt);
        boolean connected = resolveIsConnected(firebaseUid, now);
        UserAvailabilityStatus effectiveStatus = resolveEffectivePresenceStatus(connected, manualStatus);
        presenceSessionStore.setManualPresenceStatus(firebaseUid, manualStatus);
        presenceSessionStore.setEffectivePresenceStatus(firebaseUid, effectiveStatus);
        presenceSessionStore.setConnectionState(firebaseUid, connected ? CONNECTION_CONNECTED : CONNECTION_DISCONNECTED);
        return new PresenceSnapshot(manualStatus, effectiveStatus, connected ? CONNECTION_CONNECTED : CONNECTION_DISCONNECTED, lastActiveAt);
    }

    private void syncPresenceCache(String firebaseUid, UserAvailabilityStatus manualStatus) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        UserAvailabilityStatus resolvedManualStatus = manualStatus != null
                ? manualStatus
                : presenceSessionStore.getManualPresenceStatus(firebaseUid);
        UserAvailabilityStatus effectiveManual = resolvedManualStatus != null ? resolvedManualStatus : UserAvailabilityStatus.ONLINE;
        boolean connected = resolveIsConnected(firebaseUid, now);
        UserAvailabilityStatus effectiveStatus = resolveEffectivePresenceStatus(connected, effectiveManual);
        if (resolvedManualStatus != null) {
            presenceSessionStore.setManualPresenceStatus(firebaseUid, resolvedManualStatus);
        }
        presenceSessionStore.setEffectivePresenceStatus(firebaseUid, effectiveStatus);
        presenceSessionStore.setConnectionState(firebaseUid, connected ? CONNECTION_CONNECTED : CONNECTION_DISCONNECTED);
    }

    private Instant resolveLastActiveAt(String firebaseUid, Instant persistedLastActiveAt) {
        Instant cached = presenceSessionStore.getLastTouchedAt(firebaseUid);
        if (cached == null) {
            return persistedLastActiveAt;
        }
        if (persistedLastActiveAt == null || cached.isAfter(persistedLastActiveAt)) {
            return cached;
        }
        return persistedLastActiveAt;
    }

    private void rememberWebSocketActivity(String firebaseUid, String sessionId, Instant now) {
        presenceSessionStore.putSession(firebaseUid, sessionId, now);
        pruneStaleWebSocketSessions(firebaseUid, now);
    }

    private void rememberGlobalAuthenticationSession(String firebaseUid, String sessionScopeHost) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        String scopedAuthSessionId = normalizeScopedAuthSessionId(sessionScopeHost);
        presenceSessionStore.putGlobalSession(firebaseUid, scopedAuthSessionId, now);
        presenceSessionStore.clearForcedOfflineAt(firebaseUid);
        presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
    }

    private String normalizeScopedAuthSessionId(String sessionScopeHost) {
        if (sessionScopeHost == null || sessionScopeHost.isBlank()) {
            return AUTH_GLOBAL_SESSION_ID;
        }
        String normalizedHost = sessionScopeHost.trim().toLowerCase();
        return normalizedHost + ":" + AUTH_GLOBAL_SESSION_ID;
    }

    private void pruneStaleGlobalSessions(String firebaseUid, Instant now) {
        Map<String, Instant> sessions = presenceSessionStore.getGlobalSessions(firebaseUid);
        if (sessions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            if (Duration.between(entry.getValue(), now).compareTo(GLOBAL_SESSION_STALE_AFTER) > 0) {
                presenceSessionStore.removeGlobalSession(firebaseUid, entry.getKey());
            }
        }
    }

    private void pruneStaleWebSocketSessions(String firebaseUid, Instant now) {
        Map<String, Instant> sessions = presenceSessionStore.getSessions(firebaseUid);
        if (sessions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            if (Duration.between(entry.getValue(), now).compareTo(WEBSOCKET_SESSION_STALE_AFTER) > 0) {
                presenceSessionStore.removeSession(firebaseUid, entry.getKey());
            }
        }
        // WebSocket freshness is scoped to DM/socket participation. It must not force global offline.
    }

    private boolean resolveIsConnected(String firebaseUid) {
        return resolveIsConnected(firebaseUid, Instant.now());
    }

    private boolean resolveIsConnected(String firebaseUid, Instant now) {
        if (isForcedOfflineWithoutTrackedSession(firebaseUid)) {
            return false;
        }
        pruneStaleGlobalSessions(firebaseUid, now);
        pruneStaleWebSocketSessions(firebaseUid, now);
        return !presenceSessionStore.getGlobalSessions(firebaseUid).isEmpty();
    }

    private String resolveActualConnectionState(String firebaseUid) {
        return resolveIsConnected(firebaseUid) ? CONNECTION_CONNECTED : CONNECTION_DISCONNECTED;
    }

    private UserAvailabilityStatus resolveManualPresenceStatus(String firebaseUid, UserAvailabilityStatus persistedManualStatus) {
        UserAvailabilityStatus cached = presenceSessionStore.getManualPresenceStatus(firebaseUid);
        if (cached != null) {
            return cached;
        }
        UserAvailabilityStatus resolved = persistedManualStatus != null ? persistedManualStatus : UserAvailabilityStatus.ONLINE;
        presenceSessionStore.setManualPresenceStatus(firebaseUid, resolved);
        return resolved;
    }

    private boolean isForcedOfflineWithoutTrackedSession(String firebaseUid) {
        Instant forcedOfflineAt = presenceSessionStore.getForcedOfflineAt(firebaseUid);
        return forcedOfflineAt != null
                && presenceSessionStore.getGlobalSessions(firebaseUid).isEmpty()
                && presenceSessionStore.getSessions(firebaseUid).isEmpty();
    }

    private String normalizeClientSessionId(String clientSessionId) {
        if (clientSessionId == null || clientSessionId.isBlank()) {
            return "unknown";
        }
        return clientSessionId.trim();
    }

    private String websocketRefreshKey(String firebaseUid, String sessionId) {
        return firebaseUid + "::" + sessionId;
    }

    public record PresenceSnapshot(UserAvailabilityStatus manualPresenceStatus,
                                   UserAvailabilityStatus availabilityStatus,
                                   String actualConnectionState,
                                   Instant lastActiveAt) {
        public boolean online() {
            return availabilityStatus == UserAvailabilityStatus.ONLINE || availabilityStatus == UserAvailabilityStatus.IDLE;
        }
    }
}
