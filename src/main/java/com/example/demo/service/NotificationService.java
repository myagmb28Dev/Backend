package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFcmToken;
import com.example.demo.entity.enums.NotificationTargetType;
import com.example.demo.entity.enums.NotificationType;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserFcmTokenRepository;
import com.example.demo.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;
    private final UserRepository userRepository;
    private final FirebaseMessaging firebaseMessaging;

    public List<Map<String, Object>> getNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toNotificationResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> markNotificationAsRead(String notificationId) {
        User user = getCurrentUser();
        Notification notification = getOwnedNotification(user, notificationId);
        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return toNotificationResponse(saved);
    }

    @Transactional
    public Map<String, Object> markAllNotificationsAsRead() {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        long updatedCount = 0;
        for (Notification notification : notifications) {
            if (!Boolean.TRUE.equals(notification.getIsRead())) {
                notification.setIsRead(true);
                updatedCount++;
            }
        }
        notificationRepository.saveAll(notifications);
        return Map.of("updatedCount", updatedCount);
    }

    @Transactional
    public Map<String, Object> upsertFcmToken(Map<String, String> request) {
        User user = getCurrentUser();
        String token = trimToNull(request.get("token"));
        if (token == null) {
            throw new RuntimeException("FCM token은 필수입니다.");
        }

        UserFcmToken fcmToken = userFcmTokenRepository.findByToken(token)
                .orElseGet(() -> UserFcmToken.builder()
                        .token(token)
                        .build());

        fcmToken.setUser(user);
        fcmToken.setPlatform(trimToNull(request.getOrDefault("platform", "ANDROID")));
        fcmToken.setDeviceId(trimToNull(request.get("deviceId")));
        fcmToken.setActive(true);
        fcmToken.setLastSeenAt(Instant.now());

        UserFcmToken saved = userFcmTokenRepository.save(fcmToken);
        return Map.of(
                "id", saved.getId(),
                "token", saved.getToken(),
                "platform", saved.getPlatform(),
                "deviceId", saved.getDeviceId(),
                "active", saved.getActive()
        );
    }

    @Transactional
    public Map<String, Object> createAndSendNotification(
            User user,
            NotificationType type,
            NotificationTargetType targetType,
            UUID targetId,
            String title,
            String body,
            Map<String, String> data
    ) {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .title(title)
                .body(body)
                .build());

        List<UserFcmToken> activeTokens = userFcmTokenRepository.findByUserAndActiveTrueOrderByUpdatedAtDesc(user);
        int sentCount = 0;

        for (UserFcmToken fcmToken : activeTokens) {
            try {
                Message.Builder builder = Message.builder()
                        .setToken(fcmToken.getToken())
                        .putData("notificationId", notification.getId().toString())
                        .putData("type", type.name())
                        .putData("targetType", targetType.name())
                        .putData("targetId", targetId.toString())
                        .putData("title", title)
                        .putData("body", body);

                if (data != null) {
                    data.forEach((key, value) -> {
                        if (key != null && value != null) {
                            builder.putData(key, value);
                        }
                    });
                }

                firebaseMessaging.send(builder.build());
                sentCount++;
            } catch (FirebaseMessagingException e) {
                log.warn("FCM 발송 실패. tokenId={}, reason={}", fcmToken.getId(), e.getMessage());
                if (isUnregisteredToken(e)) {
                    fcmToken.setActive(false);
                    userFcmTokenRepository.save(fcmToken);
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>(toNotificationResponse(notification));
        response.put("sentCount", sentCount);
        response.put("activeTokenCount", activeTokens.size());
        return response;
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private Notification getOwnedNotification(User user, String notificationId) {
        Notification notification;
        try {
            notification = notificationRepository.findById(UUID.fromString(notificationId))
                    .orElseThrow(() -> new RuntimeException("알림을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 알림 ID 형식입니다.");
        }

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("해당 알림에 접근할 수 없습니다.");
        }
        return notification;
    }

    private Map<String, Object> toNotificationResponse(Notification notification) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", notification.getId());
        item.put("type", notification.getType().name());
        item.put("targetType", notification.getTargetType().name());
        item.put("targetId", notification.getTargetId());
        item.put("title", notification.getTitle());
        item.put("body", notification.getBody());
        item.put("isRead", notification.getIsRead());
        item.put("createdAt", notification.getCreatedAt());
        return item;
    }

    private boolean isUnregisteredToken(FirebaseMessagingException e) {
        String errorCode = String.valueOf(e.getErrorCode());
        return "registration-token-not-registered".equalsIgnoreCase(errorCode)
                || "unregistered".equalsIgnoreCase(errorCode);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
