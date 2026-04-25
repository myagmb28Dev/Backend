package com.example.pogun.service.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.presence.InMemoryPresenceSessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void snapshotReturnsOfflineWhenWebSocketSessionIsStaleDuringGrace() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("stale-user");
        Instant staleAt = Instant.now().minusSeconds(120);

        store.putSession(user.getFirebaseUid(), "session-1", staleAt);

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
        assertThat(snapshot.actualConnectionState()).isEqualTo("disconnected");
    }

    @Test
    void snapshotReturnsOfflineWhenDisconnectGraceExpired() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("stale-recent-user");
        user.setLastActiveAt(Instant.now().minusSeconds(300));

        store.setDisconnectGraceUntil(user.getFirebaseUid(), Instant.now().minusSeconds(1));
        store.setLastTouchedAt(user.getFirebaseUid(), Instant.now().minusSeconds(180));

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
    }

    @Test
    void snapshotKeepsFreshWebSocketSessionOnline() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("fresh-user");

        store.putSession(user.getFirebaseUid(), "session-1", Instant.now());

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.ONLINE);
        assertThat(snapshot.online()).isTrue();
    }

    @Test
    void touchMarksUserOnline() {
        UserPresenceService service = new UserPresenceService(userRepository, new InMemoryPresenceSessionStore());
        when(userRepository.updateLastActiveAtByFirebaseUid(eq("active-user"), any(Instant.class)))
                .thenReturn(1);

        service.touch("active-user");

        verify(userRepository).updateLastActiveAtByFirebaseUid(eq("active-user"), any(Instant.class));
    }

    @Test
    void forceOfflineClearsSessionsAndMarksUserOffline() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("logout-user");
        store.putSession(user.getFirebaseUid(), "session-1", Instant.now());

        service.forceOffline(user.getFirebaseUid());
        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        verify(userRepository).updateLastActiveAtByFirebaseUid(eq("logout-user"), any(Instant.class));
        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
    }

    @Test
    void oldWebSocketRefreshDoesNotReviveForcedOfflineUser() {
        UserPresenceService service = new UserPresenceService(userRepository, new InMemoryPresenceSessionStore());
        User user = user("old-socket-user");

        service.forceOffline(user.getFirebaseUid());
        service.refreshWebSocketSession(user.getFirebaseUid(), "old-session");
        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
    }

    @Test
    void touchDoesNotReviveForcedOfflineUserWithoutTrackedSession() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("forced-offline-user");

        service.forceOffline(user.getFirebaseUid());
        boolean touched = service.touch(user.getFirebaseUid());

        assertThat(touched).isFalse();
    }

    @Test
    void touchFromAuthenticationRevivesForcedOfflineUser() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("login-revive-user");
        when(userRepository.updateLastActiveAtByFirebaseUid(eq("login-revive-user"), any(Instant.class)))
                .thenReturn(1);

        service.forceOffline(user.getFirebaseUid());
        boolean touched = service.touchFromAuthentication(user.getFirebaseUid());

        assertThat(touched).isTrue();
        verify(userRepository, atLeastOnce()).updateLastActiveAtByFirebaseUid(eq("login-revive-user"), any(Instant.class));
    }

    @Test
    void reconcileStaleSessionsForcesOfflineAfterGrace() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("grace-expired-user");
        when(userRepository.updateLastActiveAtByFirebaseUid(eq("grace-expired-user"), any(Instant.class)))
                .thenReturn(1);

        store.setDisconnectGraceUntil(user.getFirebaseUid(), Instant.now().minusSeconds(1));

        assertThat(service.reconcileStaleSessions()).contains(user.getFirebaseUid());
        verify(userRepository).updateLastActiveAtByFirebaseUid(eq("grace-expired-user"), any(Instant.class));
    }

    @Test
    void snapshotKeepsManualIdleWhenConnected() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("manual-idle-user");
        user.setAvailabilityStatus(UserAvailabilityStatus.IDLE);

        store.putSession(user.getFirebaseUid(), "session-1", Instant.now());

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.manualPresenceStatus()).isEqualTo(UserAvailabilityStatus.IDLE);
        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.IDLE);
        assertThat(snapshot.actualConnectionState()).isEqualTo("connected");
    }

    @Test
    void snapshotKeepsManualOfflineEvenWhenConnected() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("manual-offline-user");
        user.setAvailabilityStatus(UserAvailabilityStatus.OFFLINE);

        store.putSession(user.getFirebaseUid(), "session-1", Instant.now());

        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.manualPresenceStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
        assertThat(snapshot.online()).isFalse();
        assertThat(snapshot.actualConnectionState()).isEqualTo("connected");
    }

    @Test
    void forceOfflineDoesNotOverwriteManualStatus() {
        InMemoryPresenceSessionStore store = new InMemoryPresenceSessionStore();
        UserPresenceService service = new UserPresenceService(userRepository, store);
        User user = user("manual-preserve-user");
        user.setAvailabilityStatus(UserAvailabilityStatus.IDLE);

        service.forceOffline(user.getFirebaseUid());
        UserPresenceService.PresenceSnapshot snapshot = service.snapshot(user);

        assertThat(snapshot.manualPresenceStatus()).isEqualTo(UserAvailabilityStatus.IDLE);
        assertThat(snapshot.availabilityStatus()).isEqualTo(UserAvailabilityStatus.OFFLINE);
    }

    private User user(String firebaseUid) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .availabilityStatus(UserAvailabilityStatus.ONLINE)
                .build();
    }
}
