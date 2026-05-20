package com.example.pogun.service.community;

import com.example.pogun.dto.community.CommunityPostListResponse;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import com.example.pogun.repository.user.UserFollowRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.cache.AiSourceCacheService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.storage.S3ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceSearchTest {

    @Mock
    private CommunityPostRepository communityPostRepository;
    @Mock
    private CommunityCommentRepository communityCommentRepository;
    @Mock
    private CommunityPostVoteRepository communityPostVoteRepository;
    @Mock
    private CommunityPostReactionRepository communityPostReactionRepository;
    @Mock
    private S3ImageStorageService s3ImageStorageService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFollowRepository userFollowRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AiSourceCacheService aiSourceCacheService;

    @InjectMocks
    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        when(aiSourceCacheService.currentVersion(anyString())).thenReturn("1");
        when(aiSourceCacheService.getOrLoad(anyString(), any(Duration.class), eq(CommunityPostListResponse.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<CommunityPostListResponse> loader = invocation.getArgument(3);
                    return loader.get();
                });
    }

    @Test
    void searchPostsMatchesTitleAndContentOnly() {
        CommunityPost post = CommunityPost.builder()
                .id(UUID.randomUUID())
                .author(User.builder().id(UUID.randomUUID()).nickname("작성자").build())
                .title("강아지 산책 팁")
                .content("밤 산책은 밝은 목줄이 필요합니다.")
                .category("TIP")
                .status(CommunityPostStatus.ACTIVE)
                .viewCount(3L)
                .likeCount(1L)
                .build();
        post.setCreatedAt(Instant.parse("2026-05-20T00:00:00Z"));
        when(communityPostRepository.searchPosts(eq(CommunityPostStatus.ACTIVE), eq("tip"), eq("산책"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));

        CommunityPostListResponse response = communityService.searchPosts("산책", "TIP", "LATEST", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting("title").containsExactly("강아지 산책 팁");
        verify(communityPostRepository).searchPosts(eq(CommunityPostStatus.ACTIVE), eq("tip"), eq("산책"), any(Pageable.class));
    }
}
