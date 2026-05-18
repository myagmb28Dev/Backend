package com.example.pogun.service.user;

import com.example.pogun.dto.user.UserFollowResponse;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserFollow;
import com.example.pogun.repository.user.UserFollowRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceTest {

    @Mock
    private UserFollowRepository userFollowRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private UserFollowService userFollowService;

    private User follower;
    private User following;

    @BeforeEach
    void setUp() {
        follower = user("follower");
        following = user("following");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(follower.getFirebaseUid(), null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void follow_createsFollowAndSendsNotification() {
        UUID followingId = following.getId();
        UserFollow savedFollow = UserFollow.builder()
                .id(UUID.randomUUID())
                .follower(follower)
                .following(following)
                .build();
        when(userRepository.findByFirebaseUid(follower.getFirebaseUid())).thenReturn(Optional.of(follower));
        when(userRepository.findById(followingId)).thenReturn(Optional.of(following));
        when(userFollowRepository.findByFollowerAndFollowing(follower, following)).thenReturn(Optional.empty());
        when(userFollowRepository.save(any(UserFollow.class))).thenReturn(savedFollow);

        UserFollowResponse response = userFollowService.follow(followingId.toString());

        assertThat(response.userId()).isEqualTo(followingId);
        verify(notificationService).createAndSendNotification(
                eq(following),
                eq(follower),
                eq(NotificationType.FOLLOWED_ME),
                eq(NotificationTargetType.USER),
                eq(follower.getId()),
                any(String.class),
                any(String.class),
                eq(NotificationPriority.NORMAL),
                eq("followed-me:" + follower.getId() + ":" + following.getId()),
                anyMap()
        );
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
