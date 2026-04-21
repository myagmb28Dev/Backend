package com.example.pogun.service.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);
    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(15);

    private final UserRepository userRepository;

    private final ConcurrentHashMap<String, Set<String>> activeWebSocketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastTouchedAtCache = new ConcurrentHashMap<>();

    @Transactional
    public void touch(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        Instant lastTouched = lastTouchedAtCache.get(firebaseUid);
        if (lastTouched != null && Duration.between(lastTouched, now).compareTo(TOUCH_THROTTLE) < 0) {
            return;
        }
        if (userRepository.touchLastActiveAtByFirebaseUid(firebaseUid, now) > 0) {
            lastTouchedAtCache.put(firebaseUid, now);
        }
    }

    @Transactional
    public void markWebSocketConnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        activeWebSocketSessions
                .computeIfAbsent(firebaseUid, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        touch(firebaseUid);
    }

    @Transactional
    public void markWebSocketDisconnected(String firebaseUid, String sessionId) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            activeWebSocketSessions.computeIfPresent(firebaseUid, (ignored, sessionIds) -> {
                sessionIds.remove(sessionId);
                return sessionIds.isEmpty() ? null : sessionIds;
            });
        }
        touch(firebaseUid);
    }

    @Transactional(readOnly = true)
    public PresenceSnapshot snapshot(User user) {
        if (user == null || user.getFirebaseUid() == null) {
            return new PresenceSnapshot(UserAvailabilityStatus.OFFLINE, null);
        }
        Instant lastActiveAt = resolveLastActiveAt(user.getFirebaseUid(), user.getLastActiveAt());
        boolean autoOnline = hasActiveWebSocketSession(user.getFirebaseUid())
                || (lastActiveAt != null && Duration.between(lastActiveAt, Instant.now()).compareTo(ONLINE_WINDOW) <= 0);
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

    private boolean hasActiveWebSocketSession(String firebaseUid) {
        Set<String> sessionIds = activeWebSocketSessions.get(firebaseUid);
        return sessionIds != null && !sessionIds.isEmpty();
    }

    private Instant resolveLastActiveAt(String firebaseUid, Instant persistedLastActiveAt) {
        Instant cached = lastTouchedAtCache.get(firebaseUid);
        if (cached == null) {
            return persistedLastActiveAt;
        }
        if (persistedLastActiveAt == null || cached.isAfter(persistedLastActiveAt)) {
            return cached;
        }
        return persistedLastActiveAt;
    }

    public record PresenceSnapshot(UserAvailabilityStatus availabilityStatus, Instant lastActiveAt) {
        public boolean online() {
            return availabilityStatus == UserAvailabilityStatus.ONLINE || availabilityStatus == UserAvailabilityStatus.IDLE;
        }
    }
}
