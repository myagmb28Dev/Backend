package com.example.demo.service;

import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.entity.CommunityPost;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.UserSocialAccount;
import com.example.demo.entity.enums.CommunityPostStatus;
import com.example.demo.entity.enums.PetGender;
import com.example.demo.entity.enums.PetNoticeStatus;
import com.example.demo.entity.enums.UserRole;
import com.example.demo.entity.enums.UserStatus;
import com.example.demo.repository.CommunityPostRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSocialAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetNoticeRepository petNoticeRepository;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("user@example.com")
                .nickname("기존닉네임")
                .profileImageUrl("https://example.com/old.png")
                .phoneNumber("010-0000-0000")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid-1", null)
        );

        lenient().when(userRepository.findByFirebaseUid("firebase-uid-1")).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로필 조회 시 linkedProviders를 포함한 응답을 반환한다")
    void getProfile_returnsProfileResponse() {
        given(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user))
                .willReturn(List.of(UserSocialAccount.builder().provider("GOOGLE").linked(true).build()));

        Map<String, Object> result = userService.getProfile();

        assertThat(result.get("email")).isEqualTo("user@example.com");
        assertThat(result.get("nickname")).isEqualTo("기존닉네임");
        assertThat(result.get("linkedProviders")).isEqualTo(List.of("GOOGLE"));
    }

    @Test
    @DisplayName("프로필 수정 시 변경된 값을 저장한다")
    void updateProfile_updatesUser() {
        given(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user))
                .willReturn(List.of(UserSocialAccount.builder().provider("GOOGLE").linked(true).build()));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("새닉네임");
        request.setPhoneNumber("010-1234-5678");
        request.setProfileImageUrl("https://example.com/new.png");

        Map<String, Object> result = userService.updateProfile(request);

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new.png");
        assertThat(result.get("nickname")).isEqualTo("새닉네임");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("내 실종 공고 목록 조회 시 작성한 공고 요약 목록을 반환한다")
    void myPetNotices_returnsSummaries() {
        PetNotice notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("말티즈")
                .animalType("DOG")
                .breed("말티즈")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(3L)
                .createdAt(Instant.parse("2026-03-20T12:00:00Z"))
                .build();
        given(petNoticeRepository.findByAuthorOrderByCreatedAtDesc(user)).willReturn(List.of(notice));

        List<Map<String, Object>> result = userService.myPetNotices();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("noticeId")).isEqualTo(notice.getId());
        assertThat(result.get(0).get("title")).isEqualTo("말티즈");
    }

    @Test
    @DisplayName("내 커뮤니티 글 목록 조회 시 작성한 글 요약 목록을 반환한다")
    void myCommunityPosts_returnsSummaries() {
        CommunityPost post = CommunityPost.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("커뮤니티 글")
                .content("본문")
                .status(CommunityPostStatus.ACTIVE)
                .viewCount(7L)
                .createdAt(Instant.parse("2026-03-20T12:00:00Z"))
                .build();
        given(communityPostRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId())).willReturn(List.of(post));

        List<Map<String, Object>> result = userService.myCommunityPosts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("postId")).isEqualTo(post.getId());
        assertThat(result.get(0).get("status")).isEqualTo("ACTIVE");
    }
}

