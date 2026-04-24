package com.example.pogun.service.presence;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public interface PresenceSessionStore {
    void putSession(String firebaseUid, String sessionId, Instant touchedAt);

    void removeSession(String firebaseUid, String sessionId);

    void clearSessions(String firebaseUid);

    Map<String, Instant> getSessions(String firebaseUid);

    Set<String> findUsersWithSessions();

    Instant getLastTouchedAt(String firebaseUid);

    void setLastTouchedAt(String firebaseUid, Instant touchedAt);

    void clearLastTouchedAt(String firebaseUid);

    Instant getForcedOfflineAt(String firebaseUid);

    void setForcedOfflineAt(String firebaseUid, Instant forcedOfflineAt);

    void clearForcedOfflineAt(String firebaseUid);
}
