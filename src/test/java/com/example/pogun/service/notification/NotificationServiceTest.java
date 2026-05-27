package com.example.pogun.service.notification;

import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.notification.UserNotificationSetting;
import com.example.pogun.entity.notification.UserFcmToken;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.dto.notification.NotificationDeviceDeleteResponse;
import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationFcmTokenRequest;
import com.example.pogun.dto.notification.NotificationDeviceResponse;
import com.example.pogun.repository.notification.NotificationRepository;
import com.example.pogun.repository.notification.UserFcmTokenRepository;
import com.example.pogun.repository.notification.UserNotificationSettingRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.user.UserPresenceService;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserFcmTokenRepository userFcmTokenRepository;
    @Mock
    private UserNotificationSettingRepository userNotificationSettingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FirebaseMessaging firebaseMessaging;
    @Mock
    private UserPresenceService userPresenceService;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(userPresenceService.snapshot(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserAvailabilityStatus manual = user.getAvailabilityStatus() == null
                    ? UserAvailabilityStatus.ONLINE
                    : user.getAvailabilityStatus();
            return new UserPresenceService.PresenceSnapshot(
                    manual,
                    UserAvailabilityStatus.ONLINE,
                    "connected",
                    user.getLastActiveAt()
            );
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAndSendNotification_skipsWhenTypeSettingDisabled() {
        User receiver = user("receiver");
        User actor = user("actor");
        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.DM_MESSAGE))
                .thenReturn(Optional.of(UserNotificationSetting.builder()
                        .user(receiver)
                        .type(NotificationType.DM_MESSAGE)
                        .enabled(false)
                        .build()));

        Map<String, Object> result = notificationService.createAndSendNotification(
                receiver,
                actor,
                NotificationType.DM_MESSAGE,
                NotificationTargetType.NOTICE_CHAT_MESSAGE,
                UUID.randomUUID(),
                "title",
                "body",
                NotificationPriority.HIGH,
                "dm-key",
                Map.of("roomId", UUID.randomUUID().toString())
        );

        assertThat(result).containsEntry("skipped", true);
        assertThat(result).containsEntry("reason", "NOTIFICATION_DISABLED");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createAndSendNotification_deduplicatesByDedupKey() {
        User receiver = user("receiver");
        UUID targetId = UUID.randomUUID();
        Notification existing = Notification.builder()
                .id(UUID.randomUUID())
                .user(receiver)
                .type(NotificationType.COMMUNITY_POST_LIKE)
                .targetType(NotificationTargetType.COMMUNITY_POST)
                .targetId(targetId)
                .title("title")
                .body("body")
                .priority(NotificationPriority.NORMAL)
                .metadata(Map.of("postId", targetId.toString()))
                .build();
        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.COMMUNITY_POST_LIKE))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByDedupKey("like-key")).thenReturn(Optional.of(existing));

        Map<String, Object> result = notificationService.createAndSendNotification(
                receiver,
                null,
                NotificationType.COMMUNITY_POST_LIKE,
                NotificationTargetType.COMMUNITY_POST,
                targetId,
                "title",
                "body",
                NotificationPriority.NORMAL,
                "like-key",
                Map.of("postId", targetId.toString())
        );

        assertThat(result).containsEntry("deduplicated", true);
        assertThat(result).containsEntry("sentCount", 0);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(userFcmTokenRepository, never()).findByUserAndActiveTrueOrderByUpdatedAtDesc(any());
    }

    @Test
    void createAndSendNotification_savesRecordBeforeBestEffortPush() {
        User receiver = user("receiver");
        User managedReceiver = user("receiver-managed");
        managedReceiver.setId(receiver.getId());
        UUID targetId = UUID.randomUUID();
        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.REPORT_RESULT))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(receiver.getId())).thenReturn(managedReceiver);
        when(notificationRepository.findByDedupKey("report-key")).thenReturn(Optional.empty());
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(userFcmTokenRepository.findByUserAndActiveTrueOrderByUpdatedAtDesc(managedReceiver)).thenReturn(List.of());

        Map<String, Object> result = notificationService.createAndSendNotification(
                receiver,
                null,
                NotificationType.REPORT_RESULT,
                NotificationTargetType.REPORT,
                targetId,
                "title",
                "body",
                NotificationPriority.HIGH,
                "report-key",
                Map.of("reportId", targetId.toString())
        );

        assertThat(result).containsEntry("sentCount", 0);
        assertThat(result).containsEntry("activeTokenCount", 0);
        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    void createAndSendNotification_skipsPushWhenEffectivePresenceIsIdle() {
        User receiver = user("receiver");
        User managedReceiver = user("receiver-managed");
        managedReceiver.setId(receiver.getId());
        UUID targetId = UUID.randomUUID();
        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.ADMIN_BROADCAST))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(receiver.getId())).thenReturn(managedReceiver);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(userPresenceService.snapshot(managedReceiver)).thenReturn(new UserPresenceService.PresenceSnapshot(
                UserAvailabilityStatus.IDLE,
                UserAvailabilityStatus.IDLE,
                "connected",
                managedReceiver.getLastActiveAt()
        ));

        Map<String, Object> result = notificationService.createAndSendNotification(
                receiver,
                null,
                NotificationType.ADMIN_BROADCAST,
                NotificationTargetType.ADMIN_BROADCAST,
                targetId,
                "title",
                "body",
                NotificationPriority.HIGH,
                null,
                Map.of("target", "ALL")
        );

        assertThat(result).containsEntry("skipped", true);
        assertThat(result).containsEntry("reason", "USER_IDLE");
        assertThat(result).containsEntry("effectivePresenceStatus", "IDLE");
        verify(userFcmTokenRepository, never()).findByUserAndActiveTrueOrderByUpdatedAtDesc(any());
    }

    @Test
    void createAndSendNotification_doesNotSkipPushWhenEffectivePresenceIsOffline() {
        User receiver = user("receiver");
        User managedReceiver = user("receiver-managed");
        managedReceiver.setId(receiver.getId());
        managedReceiver.setAvailabilityStatus(UserAvailabilityStatus.IDLE);
        UUID targetId = UUID.randomUUID();
        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.ADMIN_BROADCAST))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(receiver.getId())).thenReturn(managedReceiver);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(userPresenceService.snapshot(managedReceiver)).thenReturn(new UserPresenceService.PresenceSnapshot(
                UserAvailabilityStatus.IDLE,
                UserAvailabilityStatus.OFFLINE,
                "disconnected",
                managedReceiver.getLastActiveAt()
        ));
        when(userFcmTokenRepository.findByUserAndActiveTrueOrderByUpdatedAtDesc(managedReceiver)).thenReturn(List.of());

        Map<String, Object> result = notificationService.createAndSendNotification(
                receiver,
                null,
                NotificationType.ADMIN_BROADCAST,
                NotificationTargetType.ADMIN_BROADCAST,
                targetId,
                "title",
                "body",
                NotificationPriority.HIGH,
                null,
                Map.of("target", "ALL")
        );

        assertThat(result).containsEntry("reason", "NO_ACTIVE_TOKENS");
        verify(userFcmTokenRepository).findByUserAndActiveTrueOrderByUpdatedAtDesc(managedReceiver);
    }

    @Test
    void createAndSendDirectMessageNotification_skipsPushWhenRecipientAlreadyConnected() {
        User receiver = user("receiver");
        User sender = user("sender");
        User managedReceiver = user("receiver-managed");
        User managedSender = user("sender-managed");
        managedReceiver.setId(receiver.getId());
        managedSender.setId(sender.getId());
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(userNotificationSettingRepository.findByUserAndType(receiver, NotificationType.DM_MESSAGE))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(receiver.getId())).thenReturn(managedReceiver);
        when(userRepository.getReferenceById(sender.getId())).thenReturn(managedSender);
        when(notificationRepository.findByDedupKey("dm-message:" + receiver.getId() + ":" + messageId)).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(userPresenceService.snapshot(managedReceiver)).thenReturn(new UserPresenceService.PresenceSnapshot(
                UserAvailabilityStatus.ONLINE,
                UserAvailabilityStatus.ONLINE,
                "connected",
                managedReceiver.getLastActiveAt()
        ));

        notificationService.createAndSendDirectMessageNotification(
                receiver,
                sender,
                roomId,
                messageId,
                false,
                "body"
        );

        verify(notificationRepository).save(any(Notification.class));
        verify(userFcmTokenRepository, never()).findByUserAndActiveTrueOrderByUpdatedAtDesc(any());
    }

    @Test
    void upsertFcmToken_deactivatesOtherActiveTokensForSameDevice() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        when(userFcmTokenRepository.findByToken("token-1")).thenReturn(Optional.empty());
        when(userFcmTokenRepository.save(any(UserFcmToken.class))).thenAnswer(invocation -> {
            UserFcmToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });
        when(userFcmTokenRepository.deactivateActiveTokensForSameDeviceExcludingCurrent(
                eq(user), eq("WEB"), eq("device-1"), eq("token-1")
        )).thenReturn(2);

        NotificationFcmTokenResponse response = notificationService.upsertFcmToken(
                fcmTokenRequest("token-1", "WEB", "device-1")
        );

        assertThat(response.active()).isTrue();
        verify(userFcmTokenRepository).deactivateActiveTokensForSameDeviceExcludingCurrent(
                user, "WEB", "device-1", "token-1"
        );
    }

    @Test
    void upsertFcmToken_skipsDeviceDedupWhenDeviceIdMissing() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        when(userFcmTokenRepository.findByToken("token-2")).thenReturn(Optional.empty());
        when(userFcmTokenRepository.save(any(UserFcmToken.class))).thenAnswer(invocation -> {
            UserFcmToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });

        NotificationFcmTokenResponse response = notificationService.upsertFcmToken(
                fcmTokenRequest("token-2", "WEB", null)
        );

        assertThat(response.active()).isTrue();
        verify(userFcmTokenRepository, never()).deactivateActiveTokensForSameDeviceExcludingCurrent(
                any(), any(), any(), any()
        );
    }

    @Test
    void purgeInactiveFcmTokens_deletesOlderInactiveTokens() {
        when(userFcmTokenRepository.deleteInactiveTokensOlderThan(any())).thenReturn(7);

        int deleted = notificationService.purgeInactiveFcmTokens();

        assertThat(deleted).isEqualTo(7);
        verify(userFcmTokenRepository).deleteInactiveTokensOlderThan(
                argThat(cutoff -> cutoff.isBefore(Instant.now().minus(20, ChronoUnit.HOURS)))
        );
    }

    @Test
    void getDevices_returnsOwnedTokens() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        UserFcmToken token = UserFcmToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("token-1")
                .platform("WEB")
                .deviceId("device-1")
                .active(true)
                .lastSeenAt(Instant.now())
                .build();
        when(userFcmTokenRepository.findByUserOrderByUpdatedAtDesc(user)).thenReturn(List.of(token));

        List<NotificationDeviceResponse> result = notificationService.getDevices();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("device-1");
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void setDeviceActive_updatesOwnedTokenState() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        UUID tokenId = UUID.randomUUID();
        UserFcmToken token = UserFcmToken.builder()
                .id(tokenId)
                .user(user)
                .token("token-1")
                .platform("WEB")
                .deviceId("device-1")
                .active(true)
                .build();
        when(userFcmTokenRepository.findByIdAndUser(tokenId, user)).thenReturn(Optional.of(token));
        when(userFcmTokenRepository.save(token)).thenReturn(token);

        NotificationDeviceResponse response = notificationService.setDeviceActive(tokenId, false);

        assertThat(response.active()).isFalse();
        verify(userFcmTokenRepository, never()).deactivateActiveTokensForSameDeviceExcludingCurrent(
                any(), any(), any(), any()
        );
    }

    @Test
    void deleteDevice_removesAllTokensForSamePlatformAndDevice() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        UUID tokenId = UUID.randomUUID();
        UserFcmToken token = UserFcmToken.builder()
                .id(tokenId)
                .user(user)
                .token("token-1")
                .platform("WEB")
                .deviceId("device-1")
                .active(true)
                .build();
        when(userFcmTokenRepository.findByIdAndUser(tokenId, user)).thenReturn(Optional.of(token));
        when(userFcmTokenRepository.deleteByUserAndPlatformAndDeviceId(user, "WEB", "device-1")).thenReturn(3);

        NotificationDeviceDeleteResponse response = notificationService.deleteDevice(tokenId);

        assertThat(response.id()).isEqualTo(tokenId);
        assertThat(response.platform()).isEqualTo("WEB");
        assertThat(response.deviceId()).isEqualTo("device-1");
        assertThat(response.deletedCount()).isEqualTo(3);
        verify(userFcmTokenRepository).deleteByUserAndPlatformAndDeviceId(user, "WEB", "device-1");
        verify(userFcmTokenRepository, never()).delete(any(UserFcmToken.class));
    }

    @Test
    void deleteDevice_removesOnlyTokenWhenDeviceIdMissing() {
        User user = user("owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), null)
        );
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));
        UUID tokenId = UUID.randomUUID();
        UserFcmToken token = UserFcmToken.builder()
                .id(tokenId)
                .user(user)
                .token("token-1")
                .platform("WEB")
                .deviceId(null)
                .active(true)
                .build();
        when(userFcmTokenRepository.findByIdAndUser(tokenId, user)).thenReturn(Optional.of(token));

        NotificationDeviceDeleteResponse response = notificationService.deleteDevice(tokenId);

        assertThat(response.deletedCount()).isEqualTo(1);
        verify(userFcmTokenRepository).delete(token);
        verify(userFcmTokenRepository, never()).deleteByUserAndPlatformAndDeviceId(any(), any(), any());
    }

    @Test
    void markDirectMessageNotificationsAsRead_marksOnlySameRoomDirectMessageNotifications() {
        User receiver = user("receiver");
        UUID roomId = UUID.randomUUID();
        Notification dmMessage = Notification.builder()
                .id(UUID.randomUUID())
                .user(receiver)
                .type(NotificationType.DM_MESSAGE)
                .targetType(NotificationTargetType.NOTICE_CHAT_MESSAGE)
                .targetId(UUID.randomUUID())
                .title("dm")
                .body("body")
                .metadata(Map.of("roomId", roomId.toString()))
                .isRead(false)
                .build();
        Notification dmReply = Notification.builder()
                .id(UUID.randomUUID())
                .user(receiver)
                .type(NotificationType.DM_REPLY)
                .targetType(NotificationTargetType.NOTICE_CHAT_MESSAGE)
                .targetId(UUID.randomUUID())
                .title("reply")
                .body("body")
                .metadata(Map.of("roomId", roomId.toString()))
                .isRead(false)
                .build();
        when(notificationRepository.findUnreadByUserIdAndRoomIdAndTargetTypeAndTypeIn(
                receiver.getId(),
                roomId,
                NotificationTargetType.NOTICE_CHAT_MESSAGE.name(),
                List.of(NotificationType.DM_MESSAGE.name(), NotificationType.DM_REPLY.name())
        )).thenReturn(List.of(dmMessage, dmReply));

        long updatedCount = notificationService.markDirectMessageNotificationsAsRead(receiver, roomId);

        assertThat(updatedCount).isEqualTo(2L);
        assertThat(dmMessage.getIsRead()).isTrue();
        assertThat(dmReply.getIsRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(dmMessage, dmReply));
    }

    @Test
    void markDirectMessageNotificationsAsRead_skipsWhenNoMatchingNotificationExists() {
        User receiver = user("receiver");
        UUID roomId = UUID.randomUUID();
        when(notificationRepository.findUnreadByUserIdAndRoomIdAndTargetTypeAndTypeIn(
                receiver.getId(),
                roomId,
                NotificationTargetType.NOTICE_CHAT_MESSAGE.name(),
                List.of(NotificationType.DM_MESSAGE.name(), NotificationType.DM_REPLY.name())
        )).thenReturn(List.of());

        long updatedCount = notificationService.markDirectMessageNotificationsAsRead(receiver, roomId);

        assertThat(updatedCount).isZero();
        verify(notificationRepository, never()).saveAll(any());
    }

    private User user(String nickname) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(nickname + "-uid")
                .email(nickname + "@test.dev")
                .nickname(nickname)
                .build();
    }

    private NotificationFcmTokenRequest fcmTokenRequest(String token, String platform, String deviceId) {
        NotificationFcmTokenRequest request = new NotificationFcmTokenRequest();
        request.setToken(token);
        request.setPlatform(platform);
        request.setDeviceId(deviceId);
        return request;
    }
}
