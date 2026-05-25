package com.example.pogun.controller.notification;

import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.notification.NotificationRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class NotificationControllerIntegrationTest extends IntegrationTestProperties {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getNotification_returnsOwnedNotification() throws Exception {
        User me = userRepository.save(user("owner", "owner@test.dev", "owner"));
        Notification notification = notificationRepository.save(Notification.builder()
                .user(me)
                .type(NotificationType.ADMIN_BROADCAST)
                .targetType(NotificationTargetType.ADMIN_BROADCAST)
                .targetId(UUID.randomUUID())
                .title("제목")
                .body("본문")
                .priority(NotificationPriority.HIGH)
                .isRead(false)
                .build());

        mockMvc.perform(get("/api/notifications/{notificationId}", notification.getId())
                        .with(authentication(auth(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("제목"));
    }

    @Test
    void getNotification_forbiddenWhenNotOwner() throws Exception {
        User owner = userRepository.save(user("owner2", "owner2@test.dev", "owner2"));
        User other = userRepository.save(user("other", "other@test.dev", "other"));
        Notification notification = notificationRepository.save(Notification.builder()
                .user(owner)
                .type(NotificationType.ADMIN_BROADCAST)
                .targetType(NotificationTargetType.ADMIN_BROADCAST)
                .targetId(UUID.randomUUID())
                .title("제목")
                .body("본문")
                .priority(NotificationPriority.HIGH)
                .isRead(false)
                .build());

        mockMvc.perform(get("/api/notifications/{notificationId}", notification.getId())
                        .with(authentication(auth(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_FORBIDDEN"));
    }

    @Test
    void getNotification_badRequestWhenIdFormatInvalid() throws Exception {
        User me = userRepository.save(user("owner3", "owner3@test.dev", "owner3"));

        mockMvc.perform(get("/api/notifications/{notificationId}", "invalid-uuid")
                        .with(authentication(auth(me))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_NOTIFICATION_ID"));
    }

    @Test
    void sendToUser_createsNotificationForRecipient() throws Exception {
        User sender = userRepository.save(user("sender", "sender@test.dev", "sender"));
        User recipient = userRepository.save(user("recipient", "recipient@test.dev", "recipient"));

        String body = """
                {
                  "target": "specific",
                  "title": "개별 알림",
                  "body": "개별 본문",
                  "userIds": ["%s"]
                }
                """.formatted(recipient.getId());

        mockMvc.perform(post("/api/notifications/send")
                        .with(authentication(auth(sender)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.target").value("specific"))
                .andExpect(jsonPath("$.data.targetCount").value(1));

        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(recipient);
        org.assertj.core.api.Assertions.assertThat(notifications)
                .extracting(Notification::getTitle)
                .contains("개별 알림");
    }

    @Test
    void sendToAllUsers_createsNotificationsForActiveUsersExceptSender() throws Exception {
        User sender = userRepository.save(user("sender-all", "sender-all@test.dev", "sender-all"));
        User active1 = userRepository.save(user("active1", "active1@test.dev", "active1"));
        User active2 = userRepository.save(user("active2", "active2@test.dev", "active2"));
        User withdrawn = userRepository.save(user("withdrawn", "withdrawn@test.dev", "withdrawn", UserStatus.WITHDRAWN));

        String body = """
                {
                  "target": "all",
                  "title": "전체 알림",
                  "body": "전체 본문"
                }
                """;

        mockMvc.perform(post("/api/notifications/send")
                        .with(authentication(auth(sender)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.target").value("all"))
                .andExpect(jsonPath("$.data.targetCount").value(2));

        org.assertj.core.api.Assertions.assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(active1))
                .extracting(Notification::getTitle)
                .contains("전체 알림");
        org.assertj.core.api.Assertions.assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(active2))
                .extracting(Notification::getTitle)
                .contains("전체 알림");
        org.assertj.core.api.Assertions.assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(sender))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(withdrawn))
                .isEmpty();
    }

    private UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getFirebaseUid(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private User user(String uidSeed, String email, String nickname) {
        return user(uidSeed, email, nickname, UserStatus.ACTIVE);
    }

    private User user(String uidSeed, String email, String nickname, UserStatus status) {
        return User.builder()
                .firebaseUid(uidSeed + "-" + UUID.randomUUID())
                .email(email)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(status)
                .build();
    }
}
