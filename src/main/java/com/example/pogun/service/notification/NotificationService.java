package com.example.pogun.service.notification;

import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationReadAllResponse;
import com.example.pogun.dto.notification.NotificationResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.notification.UserFcmToken;
import com.example.pogun.entity.notification.NotificationTargetType;
import com.example.pogun.entity.notification.NotificationType;
import com.example.pogun.repository.notification.NotificationRepository;
import com.example.pogun.repository.notification.UserFcmTokenRepository;
import com.example.pogun.repository.user.UserRepository;
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
/**
 * 도메인 비즈니스 로직을 담당하는 NotificationService이다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;
    private final UserRepository userRepository;
    private final FirebaseMessaging firebaseMessaging;

    public List<NotificationResponse> getNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toNotificationResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markNotificationAsRead(String notificationId) {
        User user = getCurrentUser();
        Notification notification = getOwnedNotification(user, notificationId);
        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return toNotificationResponse(saved);
    }

    @Transactional
    public NotificationReadAllResponse markAllNotificationsAsRead() {
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
        return new NotificationReadAllResponse(updatedCount);
    }

    // FCM token 자체를 기준으로 upsert 해서 같은 기기 재설치나 사용자 재로그인 상황에서도 최신 메타데이터를 유지한다.
    @Transactional
    public NotificationFcmTokenResponse upsertFcmToken(Map<String, String> request) {
        User user = getCurrentUser();
        String token = trimToNull(request.get("token"));
        if (token == null) {
            throw ApiException.badRequest("MISSING_FCM_TOKEN", "FCM token은 필수입니다.");
        }

        UserFcmToken fcmToken = userFcmTokenRepository.findByToken(token)
                .orElseGet(() -> UserFcmToken.builder().token(token).build());

        fcmToken.setUser(user);
        fcmToken.setPlatform(trimToNull(request.getOrDefault("platform", "ANDROID")));
        fcmToken.setDeviceId(trimToNull(request.get("deviceId")));
        fcmToken.setActive(true);
        fcmToken.setLastSeenAt(Instant.now());

        UserFcmToken saved = userFcmTokenRepository.save(fcmToken);
        return new NotificationFcmTokenResponse(saved.getId(), saved.getToken(), saved.getPlatform(), saved.getDeviceId(), saved.getActive());
    }

    // 알림 레코드는 항상 먼저 저장하고, 푸시 발송은 best-effort 로 처리해 실패해도 알림 목록 조회는 가능하게 둔다.
    @Transactional
    public Map<String, Object> createAndSendNotification(User user, NotificationType type, NotificationTargetType targetType, UUID targetId, String title, String body, Map<String, String> data) {
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
                    data.forEach((key, value) -> { if (key != null && value != null) builder.putData(key, value); });
                }
                firebaseMessaging.send(builder.build());
                sentCount++;
            } catch (FirebaseMessagingException e) {
                log.warn("FCM 발송 실패. tokenId={}, reason={}", fcmToken.getId(), e.getMessage());
                if (isUnregisteredToken(e)) {
                    // 만료된 토큰은 즉시 비활성화해 이후 대량 발송에서 같은 실패를 반복하지 않게 한다.
                    fcmToken.setActive(false);
                    userFcmTokenRepository.save(fcmToken);
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("notification", toNotificationResponse(notification));
        response.put("sentCount", sentCount);
        response.put("activeTokenCount", activeTokens.size());
        return response;
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private Notification getOwnedNotification(User user, String notificationId) {
        Notification notification;
        try {
            notification = notificationRepository.findById(UUID.fromString(notificationId)).orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTIFICATION_ID", "올바르지 않은 알림 ID 형식입니다.");
        }
        if (!notification.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("NOTIFICATION_FORBIDDEN", "해당 알림에 접근할 수 없습니다.");
        }
        return notification;
    }

    private NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType().name(), notification.getTargetType().name(), notification.getTargetId(), notification.getTitle(), notification.getBody(), notification.getIsRead(), notification.getCreatedAt());
    }

    private boolean isUnregisteredToken(FirebaseMessagingException e) {
        String errorCode = String.valueOf(e.getErrorCode());
        return "registration-token-not-registered".equalsIgnoreCase(errorCode) || "unregistered".equalsIgnoreCase(errorCode);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}