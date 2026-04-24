package com.example.pogun.service.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.presence.PresenceSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);
    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(15);
    private static final Duration WEBSOCKET_SESSION_STALE_AFTER = Duration.ofSeconds(45);

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
        if (userRepository.updatePresenceByFirebaseUid(firebaseUid, now, UserAvailabilityStatus.ONLINE) > 0) {
            presenceSessionStore.setLastTouchedAt(firebaseUid, now);
            if (allowForcedOfflineRecovery || !presenceSessionStore.getSessions(firebaseUid).isEmpty()) {
                presenceSessionStore.clearForcedOfflineAt(firebaseUid);
            }
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
        presenceSessionStore.setForcedOfflineAt(firebaseUid, now);
        userRepository.updatePresenceByFirebaseUid(firebaseUid, now, UserAvailabilityStatus.OFFLINE);
    }

    @Transactional
    public void markWebSocketConnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        rememberWebSocketActivity(firebaseUid, sessionId, Instant.now());
        touch(firebaseUid);
    }

    public void refreshWebSocketSession(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (presenceSessionStore.getForcedOfflineAt(firebaseUid) != null) {
            return;
        }
        rememberWebSocketActivity(firebaseUid, sessionId, Instant.now());
    }

    @Transactional
    public void markWebSocketDisconnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            presenceSessionStore.removeSession(firebaseUid, sessionId);
        }
        if (hasActiveWebSocketSession(firebaseUid)) {
            touch(firebaseUid);
        } else {
            forceOffline(firebaseUid);
        }
    }

    @Transactional(readOnly = true)
    public PresenceSnapshot snapshot(User user) {
        if (user == null || user.getFirebaseUid() == null) {
            return new PresenceSnapshot(UserAvailabilityStatus.OFFLINE, null);
        }
        Instant lastActiveAt = resolveLastActiveAt(user.getFirebaseUid(), user.getLastActiveAt());
        boolean hasActiveWebSocketSession = hasActiveWebSocketSession(user.getFirebaseUid());
        boolean autoOnline = hasActiveWebSocketSession
                || isRecentlyActiveAfterForcedOffline(user.getFirebaseUid(), lastActiveAt);
        if (!autoOnline) {
            return new PresenceSnapshot(UserAvailabilityStatus.OFFLINE, lastActiveAt);
        }
        UserAvailabilityStatus configured = user.getAvailabilityStatus() != null
                ? user.getAvailabilityStatus()
                : UserAvailabilityStatus.ONLINE;
        UserAvailabilityStatus effective = configured == UserAvailabilityStatus.OFFLINE
                ? UserAvailabilityStatus.OFFLINE
                : configured;
        return new PresenceSnapshot(effective, lastActiveAt);
    }

    @Transactional
    public Set<String> reconcileStaleSessions() {
        Instant now = Instant.now();
        Set<String> changedFirebaseUids = new LinkedHashSet<>();
        for (String firebaseUid : presenceSessionStore.findUsersWithSessions()) {
            pruneStaleWebSocketSessions(firebaseUid, now);
            if (presenceSessionStore.getSessions(firebaseUid).isEmpty()
                    && presenceSessionStore.getForcedOfflineAt(firebaseUid) == null) {
                forceOffline(firebaseUid);
                changedFirebaseUids.add(firebaseUid);
            }
        }
        return changedFirebaseUids;
    }

    private boolean hasActiveWebSocketSession(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return false;
        }
        pruneStaleWebSocketSessions(firebaseUid, Instant.now());
        return !presenceSessionStore.getSessions(firebaseUid).isEmpty();
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
            presenceSessionStore.setForcedOfflineAt(firebaseUid, now);
            presenceSessionStore.clearLastTouchedAt(firebaseUid);
        }
    }

    private boolean isRecentlyActiveAfterForcedOffline(String firebaseUid, Instant lastActiveAt) {
        if (lastActiveAt == null) {
            return false;
        }
        Instant forcedOfflineAt = presenceSessionStore.getForcedOfflineAt(firebaseUid);
        if (forcedOfflineAt != null && !lastActiveAt.isAfter(forcedOfflineAt)) {
            return false;
        }
        return Duration.between(lastActiveAt, Instant.now()).compareTo(ONLINE_WINDOW) <= 0;
    }

    private boolean isForcedOfflineWithoutTrackedSession(String firebaseUid) {
        Instant forcedOfflineAt = presenceSessionStore.getForcedOfflineAt(firebaseUid);
        return forcedOfflineAt != null && presenceSessionStore.getSessions(firebaseUid).isEmpty();
    }

    public record PresenceSnapshot(UserAvailabilityStatus availabilityStatus, Instant lastActiveAt) {
        public boolean online() {
            return availabilityStatus == UserAvailabilityStatus.ONLINE || availabilityStatus == UserAvailabilityStatus.IDLE;
        }
    }
}
