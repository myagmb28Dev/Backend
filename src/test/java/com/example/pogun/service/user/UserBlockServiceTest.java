package com.example.pogun.service.user;

import com.example.pogun.dto.user.UserBlockResponse;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserBlock;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserBlockService userBlockService;

    private User blocker;
    private User blocked1;
    private User blocked2;

    @BeforeEach
    void setUp() {
        blocker = user("blocker");
        blocked1 = user("blocked1");
        blocked2 = user("blocked2");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(blocker.getFirebaseUid(), null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listBlockedUsers_returnsBlockedUsersOrdered() {
        UserBlock first = UserBlock.builder()
                .id(UUID.randomUUID())
                .blocker(blocker)
                .blocked(blocked1)
                .createdAt(Instant.parse("2026-05-25T10:00:00Z"))
                .build();
        UserBlock second = UserBlock.builder()
                .id(UUID.randomUUID())
                .blocker(blocker)
                .blocked(blocked2)
                .createdAt(Instant.parse("2026-05-25T09:00:00Z"))
                .build();

        when(userRepository.findByFirebaseUid(blocker.getFirebaseUid())).thenReturn(Optional.of(blocker));
        when(userBlockRepository.findByBlockerOrderByCreatedAtDesc(blocker)).thenReturn(List.of(first, second));

        List<UserBlockResponse> response = userBlockService.listBlockedUsers();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).userId()).isEqualTo(blocked1.getId());
        assertThat(response.get(0).nickname()).isEqualTo(blocked1.getNickname());
        assertThat(response.get(1).userId()).isEqualTo(blocked2.getId());
    }

    private User user(String nickname) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(nickname + "-uid")
                .email(nickname + "@test.dev")
                .nickname(nickname)
                .profileImageUrl("https://cdn.test/" + nickname + ".png")
                .build();
    }
}

