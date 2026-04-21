package com.example.pogun.service.notification;

import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.notification.UserNotificationSetting;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.notification.NotificationRepository;
import com.example.pogun.repository.notification.UserFcmTokenRepository;
import com.example.pogun.repository.notification.UserNotificationSettingRepository;
import com.example.pogun.repository.user.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private NotificationService notificationService;

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
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
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
        verify(notificationRepository).save(any(Notification.class));
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
}
