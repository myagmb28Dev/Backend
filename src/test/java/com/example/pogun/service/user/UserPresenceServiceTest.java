package com.example.pogun.service.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void snapshotTreatsStaleWebSocketSessionAsOffline() throws Exception {
        UserPresenceService service = new UserPresenceService(userRepository);
        User user = user("stale-user");
        Instant staleAt = Instant.now().minusSeconds(120);

        putSessionTimestamp(service, user.getFirebaseUid(), "session-1", staleAt);

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
    }

    @Test
    void snapshotKeepsFreshWebSocketSessionOnline() throws Exception {
        UserPresenceService service = new UserPresenceService(userRepository);
        User user = user("fresh-user");

        putSessionTimestamp(service, user.getFirebaseUid(), "session-1", Instant.now());

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.ONLINE);
        assertThat(snapshot.online()).isTrue();
    }

    private User user(String firebaseUid) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .availabilityStatus(UserAvailabilityStatus.ONLINE)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void putSessionTimestamp(UserPresenceService service, String firebaseUid, String sessionId, Instant timestamp) throws Exception {
        Field field = UserPresenceService.class.getDeclaredField("activeWebSocketSessions");
        field.setAccessible(true);
        ConcurrentHashMap<String, Map<String, Instant>> sessions =
                (ConcurrentHashMap<String, Map<String, Instant>>) field.get(service);
        sessions.computeIfAbsent(firebaseUid, ignored -> new ConcurrentHashMap<>()).put(sessionId, timestamp);
    }
}
