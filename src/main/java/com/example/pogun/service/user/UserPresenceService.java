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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPresenceService {

    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(15);
    private static final Duration WEBSOCKET_SESSION_STALE_AFTER = Duration.ofSeconds(45);
    private static final Duration DISCONNECT_GRACE_WINDOW = Duration.ofSeconds(40);
    private static final String CONNECTION_CONNECTED = "connected";
    private static final String CONNECTION_DISCONNECTED = "disconnected";

    private final UserRepository userRepository;
    private final PresenceSessionStore presenceSessionStore;

    @Transactional
    public boolean touch(String firebaseUid) {
        return touchInternal(firebaseUid, false);
    }

    @Transactional
    public boolean touchFromAuthentication(String firebaseUid) {
        return touchInternal(firebaseUid, true);
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
        rememberWebSocketActivity(firebaseUid, sessionId, Instant.now());
        touch(firebaseUid);
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    public void refreshWebSocketSession(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (presenceSessionStore.getForcedOfflineAt(firebaseUid) != null) {
            log.debug("[presence] skip refresh for forced-offline uid={} sessionId={}", firebaseUid, sessionId);
            return;
        }
        presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
        rememberWebSocketActivity(firebaseUid, sessionId, Instant.now());
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    @Transactional
    public void markWebSocketDisconnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            presenceSessionStore.removeSession(firebaseUid, sessionId);
        }
        Instant now = Instant.now();
        if (hasActiveWebSocketSession(firebaseUid)) {
            presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
            touch(firebaseUid);
            log.info("[presence] websocket disconnected but still active uid={} sessionId={}", firebaseUid, sessionId);
        } else {
            presenceSessionStore.setDisconnectGraceUntil(firebaseUid, now.plus(DISCONNECT_GRACE_WINDOW));
            presenceSessionStore.setLastTouchedAt(firebaseUid, now);
            log.info("[presence] websocket disconnected uid={} sessionId={} graceUntil={}", firebaseUid, sessionId, now.plus(DISCONNECT_GRACE_WINDOW));
        }
        syncPresenceCache(firebaseUid, presenceSessionStore.getManualPresenceStatus(firebaseUid));
    }

    @Transactional(readOnly = true)
    public PresenceSnapshot snapshot(User user) {
        if (user == null || user.getFirebaseUid() == null) {
            return new PresenceSnapshot(UserAvailabilityStatus.ONLINE, UserAvailabilityStatus.OFFLINE, CONNECTION_DISCONNECTED, null);
        }
        UserAvailabilityStatus manualStatus = resolveManualPresenceStatus(user.getFirebaseUid(), user.getAvailabilityStatus());
        PresenceSnapshot snapshot = buildSnapshot(user.getFirebaseUid(), manualStatus, user.getLastActiveAt());
        log.debug("[presence] snapshot uid={} manual={} connectionState={} effective={}", user.getFirebaseUid(), snapshot.manualPresenceStatus(), snapshot.actualConnectionState(), snapshot.availabilityStatus());
        return snapshot;
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
        for (String firebaseUid : presenceSessionStore.findUsersWithSessions()) {
            pruneStaleWebSocketSessions(firebaseUid, now);
            if (presenceSessionStore.getSessions(firebaseUid).isEmpty()
                    && presenceSessionStore.getForcedOfflineAt(firebaseUid) == null) {
                presenceSessionStore.setDisconnectGraceUntil(firebaseUid, now.plus(DISCONNECT_GRACE_WINDOW));
                log.info("[presence] stale session pruned uid={} graceUntil={}", firebaseUid, now.plus(DISCONNECT_GRACE_WINDOW));
            }
        }
        for (String firebaseUid : presenceSessionStore.findUsersWithDisconnectGrace()) {
            if (hasActiveWebSocketSession(firebaseUid)) {
                presenceSessionStore.clearDisconnectGraceUntil(firebaseUid);
                continue;
            }
            Instant graceUntil = presenceSessionStore.getDisconnectGraceUntil(firebaseUid);
            if (graceUntil == null) {
                continue;
            }
            if (!now.isBefore(graceUntil) && presenceSessionStore.getForcedOfflineAt(firebaseUid) == null) {
                forceOffline(firebaseUid);
                changedFirebaseUids.add(firebaseUid);
            }
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
        Instant lastActiveAt = resolveLastActiveAt(firebaseUid, persistedLastActiveAt);
        boolean connected = resolveIsConnected(firebaseUid);
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
        UserAvailabilityStatus resolvedManualStatus = manualStatus != null
                ? manualStatus
                : presenceSessionStore.getManualPresenceStatus(firebaseUid);
        UserAvailabilityStatus effectiveManual = resolvedManualStatus != null ? resolvedManualStatus : UserAvailabilityStatus.ONLINE;
        boolean connected = resolveIsConnected(firebaseUid);
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

    private void pruneStaleWebSocketSessions(String firebaseUid, Instant now) {
        Map<String, Instant> sessions = presenceSessionStore.getSessions(firebaseUid);
        if (sessions.isEmpty()) {
            return;
        }
        boolean removedAny = false;
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            if (Duration.between(entry.getValue(), now).compareTo(WEBSOCKET_SESSION_STALE_AFTER) > 0) {
                presenceSessionStore.removeSession(firebaseUid, entry.getKey());
                removedAny = true;
            }
        }
        if (removedAny && presenceSessionStore.getSessions(firebaseUid).isEmpty()) {
            presenceSessionStore.setDisconnectGraceUntil(firebaseUid, now.plus(DISCONNECT_GRACE_WINDOW));
        }
    }

    private boolean isWithinDisconnectGrace(String firebaseUid) {
        Instant graceUntil = presenceSessionStore.getDisconnectGraceUntil(firebaseUid);
        return graceUntil != null && Instant.now().isBefore(graceUntil);
    }

    private boolean resolveIsConnected(String firebaseUid) {
        if (isForcedOfflineWithoutTrackedSession(firebaseUid)) {
            return false;
        }
        return hasActiveWebSocketSession(firebaseUid) || isWithinDisconnectGrace(firebaseUid);
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
        return forcedOfflineAt != null && presenceSessionStore.getSessions(firebaseUid).isEmpty();
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
