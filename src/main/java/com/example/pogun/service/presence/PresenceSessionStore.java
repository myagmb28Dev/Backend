package com.example.pogun.service.presence;

import com.example.pogun.entity.user.enums.UserAvailabilityStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public interface PresenceSessionStore {
    void putGlobalSession(String firebaseUid, String clientSessionId, Instant touchedAt);

    void removeGlobalSession(String firebaseUid, String clientSessionId);

    void clearGlobalSessions(String firebaseUid);

    Map<String, Instant> getGlobalSessions(String firebaseUid);

    Set<String> findUsersWithGlobalSessions();

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

    Instant getDisconnectGraceUntil(String firebaseUid);

    void setDisconnectGraceUntil(String firebaseUid, Instant disconnectGraceUntil);

    void clearDisconnectGraceUntil(String firebaseUid);

    Set<String> findUsersWithDisconnectGrace();

    UserAvailabilityStatus getManualPresenceStatus(String firebaseUid);

    void setManualPresenceStatus(String firebaseUid, UserAvailabilityStatus manualPresenceStatus);

    void clearManualPresenceStatus(String firebaseUid);

    UserAvailabilityStatus getEffectivePresenceStatus(String firebaseUid);

    void setEffectivePresenceStatus(String firebaseUid, UserAvailabilityStatus effectivePresenceStatus);

    void clearEffectivePresenceStatus(String firebaseUid);

    String getConnectionState(String firebaseUid);

    void setConnectionState(String firebaseUid, String connectionState);

    void clearConnectionState(String firebaseUid);
}
