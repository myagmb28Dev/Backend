package com.example.pogun.service.admin;

import com.example.pogun.dto.admin.AdminNotificationSendRequest;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.adminauth.AdminSecurityService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AdminNotificationSendIntegrationTest extends IntegrationTestProperties {

    @Autowired
    private AdminConsoleService adminConsoleService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private AdminSecurityService adminSecurityService;

    @BeforeEach
    void setUp() {
        doNothing().when(adminSecurityService).require(any());
        when(notificationService.createAndSendNotification(
                any(User.class),
                any(User.class),
                eq(NotificationType.ADMIN_BROADCAST),
                eq(NotificationTargetType.ADMIN_BROADCAST),
                any(UUID.class),
                any(String.class),
                any(String.class),
                eq(NotificationPriority.HIGH),
                eq(null),
                any(Map.class)
        )).thenReturn(Map.of(
                "sentCount", 1,
                "failedTokenCount", 0
        ));
    }

    @Test
    void sendNotification_specificTarget_sendsToRequestedUsers() {
        User actor = userRepository.save(user("admin-actor", "admin-actor@local.dev", "관리자", UserRole.ADMIN, UserStatus.ACTIVE));
        User recipient = userRepository.save(user("target-user", "target-user@local.dev", "대상", UserRole.USER, UserStatus.ACTIVE));
        when(adminSecurityService.getCurrentAdminUser()).thenReturn(actor);

        AdminNotificationSendRequest request = new AdminNotificationSendRequest();
        request.setTarget("specific");
        request.setTitle("테스트 알림");
        request.setBody("개별 대상 알림");
        request.setUserIds(List.of(recipient.getId().toString()));

        Map<String, Object> response = adminConsoleService.sendNotification(request);

        assertThat(response.get("target")).isEqualTo("specific");
        assertThat(response.get("targetCount")).isEqualTo(1);
        verify(notificationService, times(1)).createAndSendNotification(
                argThat(user -> user != null && recipient.getId().equals(user.getId())),
                argThat(user -> user != null && actor.getId().equals(user.getId())),
                eq(NotificationType.ADMIN_BROADCAST),
                eq(NotificationTargetType.ADMIN_BROADCAST),
                eq(recipient.getId()),
                eq("테스트 알림"),
                eq("개별 대상 알림"),
                eq(NotificationPriority.HIGH),
                eq(null),
                any(Map.class)
        );
    }

    @Test
    void sendNotification_specificTarget_withoutUserIds_throwsBadRequest() {
        User actor = userRepository.save(user("admin-actor-2", "admin-actor-2@local.dev", "관리자2", UserRole.ADMIN, UserStatus.ACTIVE));
        when(adminSecurityService.getCurrentAdminUser()).thenReturn(actor);

        AdminNotificationSendRequest request = new AdminNotificationSendRequest();
        request.setTarget("specific");
        request.setTitle("테스트 알림");
        request.setBody("개별 대상 알림");

        assertThatThrownBy(() -> adminConsoleService.sendNotification(request))
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getCode()).isEqualTo("MISSING_NOTIFICATION_USERS");
                });
    }

    private User user(String uidSeed, String email, String nickname, UserRole role, UserStatus status) {
        return User.builder()
                .firebaseUid(uidSeed + "-" + UUID.randomUUID())
                .email(email)
                .nickname(nickname)
                .role(role)
                .status(status)
                .build();
    }
}
