package com.example.pogun.service.community;

import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentDeleteResponse;
import com.example.pogun.dto.community.CommunityCommentResponse;
import com.example.pogun.dto.community.CommunityPostRequest;
import com.example.pogun.dto.community.CommunityPostUpdateRequest;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostImage;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.community.CommunityImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityPostVoteRepository communityPostVoteRepository;

    @Mock
    private CommunityPostReactionRepository communityPostReactionRepository;

    @Mock
    private CommunityImageStorageService communityImageStorageService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommunityService communityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPostList_usesRequestedPageAndSize() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440030"))
                .author(author)
                .title("제목")
                .content("본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        post.getTags().addAll(List.of("dog"));
        post.getImages().add(CommunityPostImage.builder()
                .post(post)
                .imageUrl("https://example.com/thumb.jpg")
                .sortOrder(0)
                .build());

        Page<CommunityPost> page = new PageImpl<>(List.of(post));
        given(communityPostRepository.findPosts(eq(CommunityPostStatus.ACTIVE), eq("FREE"), eq("dog"), eq("검색"), any(Pageable.class)))
                .willReturn(page);

        var result = communityService.getPostList("POPULAR", "FREE", "dog", "검색", 2, 50);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityPostRepository).findPosts(eq(CommunityPostStatus.ACTIVE), eq("FREE"), eq("dog"), eq("검색"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("viewCount")).isNotNull();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).authorNickname()).isEqualTo("작성자");
        assertThat(result.items().get(0).thumbnailImageUrl()).isEqualTo("https://example.com/thumb.jpg");
    }

    @Test
    void updatePost_allowsPartialUpdateWithoutClearingOtherFields() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440031"))
                .author(author)
                .title("기존 제목")
                .content("기존 본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        post.getTags().addAll(List.of("dog", "walk"));
        post.setLikeCount(3L);

        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        given(communityPostRepository.findById(post.getId())).willReturn(Optional.of(post));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        CommunityPostUpdateRequest request = new CommunityPostUpdateRequest();
        request.setTitle("새 제목");

        var result = communityService.updatePost(post.getId().toString(), request);

        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
        assertThat(post.getCategory()).isEqualTo("FREE");
        assertThat(post.getTags()).containsExactly("dog", "walk");
        assertThat(result.updated()).isTrue();
        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void createPost_rejectsInvalidPollConfiguration() {
        User author = user("작성자");
        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("제목");
        request.setContent("본문");
        request.setPollQuestion("어떤 사료가 좋아요?");
        request.setPollOptions(List.of("A"));

        assertThatThrownBy(() -> communityService.createPost(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("투표 옵션은 2개 이상");

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void createPost_storesLocalImages() {
        User author = user("작성자");
        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        given(communityImageStorageService.storeImages(eq(author.getId()), any())).willReturn(List.of("/uploads/community/posts/" + author.getId() + "/a.jpg", "/uploads/community/posts/" + author.getId() + "/b.jpg"));
        given(communityPostRepository.save(any(CommunityPost.class))).willAnswer(invocation -> { CommunityPost post = invocation.getArgument(0); post.setId(UUID.randomUUID()); return post; });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("제목");
        request.setContent("본문");

        MultipartFile image1 = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46});
        MultipartFile image2 = new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x11, 0x45, 0x58});

        var result = communityService.createPost(request, List.of(image1, image2));

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo("제목");
        assertThat(communityImageStorageService).isNotNull();
        verify(communityImageStorageService).storeImages(eq(author.getId()), any());
        verify(communityPostRepository).save(any(CommunityPost.class));
    }
    @Test
    void deletePost_marksPostAndCommentsAsDeleted() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440032"))
                .author(author)
                .title("기존 제목")
                .content("기존 본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        CommunityComment comment = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440033"))
                .post(post)
                .author(author)
                .content("댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();

        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        given(communityPostRepository.findById(post.getId())).willReturn(Optional.of(post));
        given(communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL)).willReturn(List.of(comment));
        given(communityPostRepository.save(post)).willReturn(post);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        var result = communityService.deletePost(post.getId().toString());

        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.DELETED);
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.DELETED);
        assertThat(result.deleted()).isTrue();
        verify(communityPostRepository).save(post);
    }

    @Test
    void deleteComment_marksCommentAsDeleted() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440034"))
                .author(author)
                .title("기존 제목")
                .content("기존 본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        CommunityComment comment = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440035"))
                .post(post)
                .author(author)
                .content("댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();

        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        given(communityPostRepository.findById(post.getId())).willReturn(Optional.of(post));
        given(communityCommentRepository.findById(comment.getId())).willReturn(Optional.of(comment));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        var result = communityService.deleteComment(post.getId().toString(), comment.getId().toString());

        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.DELETED);
        assertThat(result.deleted()).isTrue();
        assertThat(result.postId()).isEqualTo(post.getId());
    }

    @Test
    void createComment_supportsReplies() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440036"))
                .author(author)
                .title("기존 제목")
                .content("기존 본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        CommunityComment parent = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440037"))
                .post(post)
                .author(author)
                .content("부모 댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();
        CommunityComment saved = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440038"))
                .post(post)
                .author(author)
                .parentComment(parent)
                .content("대댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();

        given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(author));
        given(communityPostRepository.findById(post.getId())).willReturn(Optional.of(post));
        given(communityCommentRepository.findById(parent.getId())).willReturn(Optional.of(parent));
        given(communityCommentRepository.save(any(CommunityComment.class))).willReturn(saved);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("firebase-uid", "N/A"));

        CommunityCommentRequest request = new CommunityCommentRequest();
        request.setContent("대댓글");
        request.setParentCommentId(parent.getId());

        var result = communityService.createComment(post.getId().toString(), request);

        assertThat(result.parentCommentId()).isEqualTo(parent.getId());
    }

    @Test
    void getComments_returnsNestedReplies() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440039"))
                .author(author)
                .title("기존 제목")
                .content("기존 본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        CommunityComment parent = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440040"))
                .post(post)
                .author(author)
                .content("부모 댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();
        CommunityComment child = CommunityComment.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440041"))
                .post(post)
                .author(author)
                .parentComment(parent)
                .content("대댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();
        parent.setCreatedAt(java.time.Instant.parse("2026-03-20T10:00:00Z"));
        child.setCreatedAt(java.time.Instant.parse("2026-03-20T10:05:00Z"));

        given(communityPostRepository.findById(post.getId())).willReturn(Optional.of(post));
        given(communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL)).willReturn(List.of(parent, child));

        List<CommunityCommentResponse> result = communityService.getComments(post.getId().toString());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).replies()).hasSize(1);
        assertThat(result.get(0).replies().get(0).parentCommentId()).isEqualTo(parent.getId());
    }

    @Test
    void getPostList_defaultsToLatestSort() {
        User author = user("작성자");
        CommunityPost post = CommunityPost.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"))
                .author(author)
                .title("제목")
                .content("본문")
                .category("FREE")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        given(communityPostRepository.findPosts(eq(CommunityPostStatus.ACTIVE), eq("FREE"), eq("dog"), eq("검색"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));

        communityService.getPostList("LATEST", "FREE", "dog", "검색", 0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityPostRepository).findPosts(eq(CommunityPostStatus.ACTIVE), eq("FREE"), eq("dog"), eq("검색"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
    }
    private User user(String nickname) {
        return User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440099"))
                .firebaseUid("firebase-uid")
                .email("firebase-uid@example.com")
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}













