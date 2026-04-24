package com.example.pogun.service.presence;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RedisPresenceSessionStore implements PresenceSessionStore {
    private static final String USER_SESSIONS_PREFIX = "presence:user:sessions:";
    private static final String USERS_WITH_SESSIONS_KEY = "presence:user:sessions:users";
    private static final String LAST_TOUCHED_PREFIX = "presence:user:lastTouched:";
    private static final String FORCED_OFFLINE_PREFIX = "presence:user:forcedOffline:";
    private static final String DISCONNECT_GRACE_PREFIX = "presence:user:disconnectGraceUntil:";
    private static final String USERS_WITH_DISCONNECT_GRACE_KEY = "presence:user:disconnectGrace:users";
    private static final Duration USER_META_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void putSession(String firebaseUid, String sessionId, Instant touchedAt) {
        String key = sessionsKey(firebaseUid);
        redisTemplate.opsForHash().put(key, sessionId, String.valueOf(touchedAt.toEpochMilli()));
        redisTemplate.opsForSet().add(USERS_WITH_SESSIONS_KEY, firebaseUid);
        clearDisconnectGraceUntil(firebaseUid);
    }

    @Override
    public void removeSession(String firebaseUid, String sessionId) {
        String key = sessionsKey(firebaseUid);
        redisTemplate.opsForHash().delete(key, sessionId);
        Long size = redisTemplate.opsForHash().size(key);
        if (size != null && size == 0L) {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(USERS_WITH_SESSIONS_KEY, firebaseUid);
        }
    }

    @Override
    public void clearSessions(String firebaseUid) {
        redisTemplate.delete(sessionsKey(firebaseUid));
        redisTemplate.opsForSet().remove(USERS_WITH_SESSIONS_KEY, firebaseUid);
    }

    @Override
    public Map<String, Instant> getSessions(String firebaseUid) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(sessionsKey(firebaseUid));
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<String, Instant> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                long epochMillis = Long.parseLong(entry.getValue().toString());
                result.put(entry.getKey().toString(), Instant.ofEpochMilli(epochMillis));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @Override
    public Set<String> findUsersWithSessions() {
        Set<String> members = redisTemplate.opsForSet().members(USERS_WITH_SESSIONS_KEY);
        return members == null ? Set.of() : members;
    }

    @Override
    public Instant getLastTouchedAt(String firebaseUid) {
        return getInstantValue(lastTouchedKey(firebaseUid));
    }

    @Override
    public void setLastTouchedAt(String firebaseUid, Instant touchedAt) {
        setInstantValue(lastTouchedKey(firebaseUid), touchedAt);
    }

    @Override
    public void clearLastTouchedAt(String firebaseUid) {
        redisTemplate.delete(lastTouchedKey(firebaseUid));
    }

    @Override
    public Instant getForcedOfflineAt(String firebaseUid) {
        return getInstantValue(forcedOfflineKey(firebaseUid));
    }

    @Override
    public void setForcedOfflineAt(String firebaseUid, Instant forcedOfflineAt) {
        setInstantValue(forcedOfflineKey(firebaseUid), forcedOfflineAt);
    }

    @Override
    public void clearForcedOfflineAt(String firebaseUid) {
        redisTemplate.delete(forcedOfflineKey(firebaseUid));
    }

    @Override
    public Instant getDisconnectGraceUntil(String firebaseUid) {
        return getInstantValue(disconnectGraceKey(firebaseUid));
    }

    @Override
    public void setDisconnectGraceUntil(String firebaseUid, Instant disconnectGraceUntil) {
        setInstantValue(disconnectGraceKey(firebaseUid), disconnectGraceUntil);
        redisTemplate.opsForSet().add(USERS_WITH_DISCONNECT_GRACE_KEY, firebaseUid);
    }

    @Override
    public void clearDisconnectGraceUntil(String firebaseUid) {
        redisTemplate.delete(disconnectGraceKey(firebaseUid));
        redisTemplate.opsForSet().remove(USERS_WITH_DISCONNECT_GRACE_KEY, firebaseUid);
    }

    @Override
    public Set<String> findUsersWithDisconnectGrace() {
        Set<String> members = redisTemplate.opsForSet().members(USERS_WITH_DISCONNECT_GRACE_KEY);
        return members == null ? Set.of() : members;
    }

    private void setInstantValue(String key, Instant value) {
        redisTemplate.opsForValue().set(key, String.valueOf(value.toEpochMilli()), USER_META_TTL);
    }

    private Instant getInstantValue(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sessionsKey(String firebaseUid) {
        return USER_SESSIONS_PREFIX + firebaseUid;
    }

    private String lastTouchedKey(String firebaseUid) {
        return LAST_TOUCHED_PREFIX + firebaseUid;
    }

    private String forcedOfflineKey(String firebaseUid) {
        return FORCED_OFFLINE_PREFIX + firebaseUid;
    }

    private String disconnectGraceKey(String firebaseUid) {
        return DISCONNECT_GRACE_PREFIX + firebaseUid;
    }
}
