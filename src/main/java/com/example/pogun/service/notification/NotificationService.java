package com.example.pogun.service.notification;

import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationListResponse;
import com.example.pogun.dto.notification.NotificationReadAllResponse;
import com.example.pogun.dto.notification.NotificationResponse;
import com.example.pogun.dto.notification.NotificationSettingItemRequest;
import com.example.pogun.dto.notification.NotificationSettingResponse;
import com.example.pogun.dto.notification.NotificationSettingsUpdateRequest;
import com.example.pogun.dto.notification.NotificationUnreadCountResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.notification.UserFcmToken;
import com.example.pogun.entity.notification.UserNotificationSetting;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.notification.NotificationRepository;
import com.example.pogun.repository.notification.UserFcmTokenRepository;
import com.example.pogun.repository.notification.UserNotificationSettingRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.user.UserPresenceService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 NotificationService이다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MIN_INACTIVE_TOKEN_RETENTION_DAYS = 1;

    private final NotificationRepository notificationRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final UserRepository userRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final UserPresenceService userPresenceService;
    @Value("${app.notification.inactive-token-retention-days:30}")
    private int inactiveTokenRetentionDays;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(int page, int size, Boolean unreadOnly, String type) {
        User user = getCurrentUser();
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.min(Math.max(size, 1), 100);
        NotificationType notificationType = parseNullableType(type);
        PageRequest pageRequest = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        Page<Notification> notifications;
        if (notificationType != null && onlyUnread) {
            notifications = notificationRepository.findByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(user, notificationType, pageRequest);
        } else if (notificationType != null) {
            notifications = notificationRepository.findByUserAndTypeOrderByCreatedAtDesc(user, notificationType, pageRequest);
        } else if (onlyUnread) {
            notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user, pageRequest);
        } else {
            notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageRequest);
        }
        return new NotificationListResponse(
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getContent().stream().map(this::toNotificationResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount() {
        User user = getCurrentUser();
        return new NotificationUnreadCountResponse(notificationRepository.countByUserAndIsReadFalse(user));
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

    @Transactional
    public long markDirectMessageNotificationsAsRead(User user, UUID roomId) {
        if (user == null || user.getId() == null || roomId == null) {
            return 0L;
        }
        List<Notification> notifications = notificationRepository.findUnreadByUserIdAndRoomIdAndTargetTypeAndTypeIn(
                user.getId(),
                roomId,
                NotificationTargetType.NOTICE_CHAT_MESSAGE.name(),
                List.of(NotificationType.DM_MESSAGE.name(), NotificationType.DM_REPLY.name())
        );
        if (notifications.isEmpty()) {
            return 0L;
        }
        notifications.forEach(notification -> notification.setIsRead(true));
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> getSettings() {
        User user = getCurrentUser();
        Map<NotificationType, Boolean> savedSettings = new LinkedHashMap<>();
        for (UserNotificationSetting setting : userNotificationSettingRepository.findByUser(user)) {
            savedSettings.put(setting.getType(), Boolean.TRUE.equals(setting.getEnabled()));
        }
        return Arrays.stream(NotificationType.values())
                .map(type -> new NotificationSettingResponse(type.name(), savedSettings.getOrDefault(type, true)))
                .toList();
    }

    @Transactional
    public List<NotificationSettingResponse> updateSettings(NotificationSettingsUpdateRequest request) {
        User user = getCurrentUser();
        if (request == null || request.getSettings() == null || request.getSettings().isEmpty()) {
            throw ApiException.badRequest("MISSING_NOTIFICATION_SETTINGS", "알림 설정은 필수입니다.");
        }
        for (NotificationSettingItemRequest item : request.getSettings()) {
            NotificationType type = parseType(item.getType());
            UserNotificationSetting setting = userNotificationSettingRepository.findByUserAndType(user, type)
                    .orElseGet(() -> UserNotificationSetting.builder()
                            .user(user)
                            .type(type)
                            .enabled(true)
                            .build());
            setting.setEnabled(Boolean.TRUE.equals(item.getEnabled()));
            userNotificationSettingRepository.save(setting);
        }
        return getSettings();
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
        String platform = trimToNull(request.getOrDefault("platform", "ANDROID"));
        String deviceId = trimToNull(request.get("deviceId"));
        fcmToken.setPlatform(platform);
        fcmToken.setDeviceId(deviceId);
        fcmToken.setActive(true);
        fcmToken.setLastSeenAt(Instant.now());

        UserFcmToken saved = userFcmTokenRepository.save(fcmToken);
        deactivateOtherActiveTokensForSameDevice(user, platform, deviceId, token);
        return new NotificationFcmTokenResponse(saved.getId(), saved.getToken(), saved.getPlatform(), saved.getDeviceId(), saved.getActive());
    }

    @Transactional
    public int purgeInactiveFcmTokens() {
        int retentionDays = Math.max(inactiveTokenRetentionDays, MIN_INACTIVE_TOKEN_RETENTION_DAYS);
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = userFcmTokenRepository.deleteInactiveTokensOlderThan(cutoff);
        if (deleted > 0) {
            log.info("inactive fcm tokens purged. retentionDays={}, deleted={}", retentionDays, deleted);
        }
        return deleted;
    }

    // 알림 레코드는 항상 먼저 저장하고, 푸시 발송은 best-effort 로 처리해 실패해도 알림 목록 조회는 가능하게 둔다.
    @Transactional
    public Map<String, Object> createAndSendNotification(User user, NotificationType type, NotificationTargetType targetType, UUID targetId, String title, String body, Map<String, String> data) {
        return createAndSendNotification(user, null, type, targetType, targetId, title, body, NotificationPriority.NORMAL, null, data);
    }

    @Transactional
    public Map<String, Object> createAndSendNotification(
            User user,
            User actorUser,
            NotificationType type,
            NotificationTargetType targetType,
            UUID targetId,
            String title,
            String body,
            NotificationPriority priority,
            String dedupKey,
            Map<String, String> metadata
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (user == null || type == null || targetType == null || targetId == null) {
            response.put("skipped", true);
            response.put("reason", "INVALID_NOTIFICATION_REQUEST");
            return response;
        }
        if (actorUser != null && Objects.equals(user.getId(), actorUser.getId())) {
            response.put("skipped", true);
            response.put("reason", "SELF_NOTIFICATION");
            return response;
        }
        User managedUser = userRepository.getReferenceById(user.getId());
        User managedActorUser = actorUser != null ? userRepository.getReferenceById(actorUser.getId()) : null;
        if (!isNotificationEnabled(user, type)) {
            response.put("skipped", true);
            response.put("reason", "NOTIFICATION_DISABLED");
            return response;
        }
        String normalizedDedupKey = trimToNull(dedupKey);
        if (normalizedDedupKey != null) {
            Notification existing = notificationRepository.findByDedupKey(normalizedDedupKey).orElse(null);
            if (existing != null) {
                response.put("notification", toNotificationResponse(existing));
                response.put("sentCount", 0);
                response.put("activeTokenCount", 0);
                response.put("failedTokenCount", 0);
                response.put("deduplicated", true);
                response.put("skipped", true);
                response.put("reason", "DEDUPLICATED");
                return response;
            }
        }

        Map<String, String> safeMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    safeMetadata.put(key, value);
                }
            });
        }
        Notification notification = notificationRepository.saveAndFlush(Notification.builder()
                .user(managedUser)
                .actorUser(managedActorUser)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .title(title)
                .body(body)
                .priority(priority == null ? NotificationPriority.NORMAL : priority)
                .dedupKey(normalizedDedupKey)
                .metadata(safeMetadata)
                .build());

        UserAvailabilityStatus effectivePresenceStatus = userPresenceService.snapshot(managedUser).availabilityStatus();
        if (effectivePresenceStatus == UserAvailabilityStatus.IDLE) {
            response.put("notification", toNotificationResponse(notification));
            response.put("sentCount", 0);
            response.put("activeTokenCount", 0);
            response.put("failedTokenCount", 0);
            response.put("skipped", true);
            response.put("pushSkipped", true);
            response.put("reason", "USER_IDLE");
            response.put("effectivePresenceStatus", effectivePresenceStatus.name());
            return response;
        }

        List<UserFcmToken> activeTokens = userFcmTokenRepository.findByUserAndActiveTrueOrderByUpdatedAtDesc(managedUser);
        int sentCount = 0;
        int failedTokenCount = 0;
        int deactivatedTokenCount = 0;

        for (UserFcmToken fcmToken : activeTokens) {
            try {
                Message.Builder builder = Message.builder()
                        .setToken(fcmToken.getToken())
                        .putData("notificationId", notification.getId().toString())
                        .putData("type", type.name())
                        .putData("targetType", targetType.name())
                        .putData("targetId", targetId.toString())
                        .putData("title", title)
                        .putData("body", body)
                        .putData("priority", notification.getPriority().name());
                safeMetadata.forEach(builder::putData);
                firebaseMessaging.send(builder.build());
                sentCount++;
            } catch (Exception e) {
                failedTokenCount++;
                log.warn("FCM 발송 실패. tokenId={}, reason={}", fcmToken.getId(), e.getMessage());
                if (e instanceof FirebaseMessagingException firebaseMessagingException && isUnregisteredToken(firebaseMessagingException)) {
                    // 만료된 토큰은 즉시 비활성화해 이후 대량 발송에서 같은 실패를 반복하지 않게 한다.
                    fcmToken.setActive(false);
                    userFcmTokenRepository.save(fcmToken);
                    deactivatedTokenCount++;
                }
            }
        }

        response.put("notification", toNotificationResponse(notification));
        response.put("sentCount", sentCount);
        response.put("activeTokenCount", activeTokens.size());
        response.put("failedTokenCount", failedTokenCount);
        response.put("deactivatedTokenCount", deactivatedTokenCount);
        if (activeTokens.isEmpty()) {
            response.put("skipped", true);
            response.put("reason", "NO_ACTIVE_TOKENS");
        }
        return response;
    }

    public boolean isNotificationEnabled(User user, NotificationType type) {
        return userNotificationSettingRepository.findByUserAndType(user, type)
                .map(UserNotificationSetting::getEnabled)
                .orElse(true);
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
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTargetType().name(),
                notification.getTargetId(),
                notification.getActorUser() != null ? notification.getActorUser().getId() : null,
                notification.getTitle(),
                notification.getBody(),
                notification.getPriority().name(),
                notification.getMetadata() == null ? Map.of() : Map.copyOf(notification.getMetadata()),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }

    private NotificationType parseNullableType(String type) {
        if (trimToNull(type) == null) {
            return null;
        }
        return parseType(type);
    }

    private NotificationType parseType(String type) {
        try {
            return NotificationType.valueOf(trimToNull(type).toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_NOTIFICATION_TYPE", "올바르지 않은 알림 타입입니다.");
        }
    }

    private boolean isUnregisteredToken(FirebaseMessagingException e) {
        String errorCode = String.valueOf(e.getErrorCode());
        String message = String.valueOf(e.getMessage());
        return "registration-token-not-registered".equalsIgnoreCase(errorCode)
                || "unregistered".equalsIgnoreCase(errorCode)
                || "UNREGISTERED".equalsIgnoreCase(errorCode)
                || "notregistered".equalsIgnoreCase(errorCode)
                || message.toLowerCase().contains("registration-token-not-registered")
                || message.toLowerCase().contains("requested entity was not found")
                || message.toLowerCase().contains("device unregistered")
                || message.toLowerCase().contains("notregistered");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void deactivateOtherActiveTokensForSameDevice(User user, String platform, String deviceId, String currentToken) {
        if (user == null || platform == null || deviceId == null || currentToken == null) {
            return;
        }
        int deactivated = userFcmTokenRepository.deactivateActiveTokensForSameDeviceExcludingCurrent(
                user,
                platform,
                deviceId,
                currentToken
        );
        if (deactivated > 0) {
            log.info("deactivated stale active tokens for same device. userId={}, platform={}, deviceId={}, deactivated={}",
                    user.getId(),
                    platform,
                    deviceId,
                    deactivated);
        }
    }
}
