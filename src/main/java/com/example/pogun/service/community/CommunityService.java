package com.example.pogun.service.community;

import com.example.pogun.dto.community.CommunityCommentCreateResponse;
import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentResponse;
import com.example.pogun.dto.community.CommunityPollResponse;
import com.example.pogun.dto.community.CommunityPostCreateResponse;
import com.example.pogun.dto.community.CommunityPostDeleteResponse;
import com.example.pogun.dto.community.CommunityPostDetailResponse;
import com.example.pogun.dto.community.CommunityPostListResponse;
import com.example.pogun.dto.community.CommunityPostRequest;
import com.example.pogun.dto.community.CommunityPostSummaryResponse;
import com.example.pogun.dto.community.CommunityPostUpdateResponse;
import com.example.pogun.dto.community.CommunityReactionRequest;
import com.example.pogun.dto.community.CommunityReactionResponse;
import com.example.pogun.dto.community.CommunityVoteRequest;
import com.example.pogun.dto.community.CommunityVoteResponse;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostImage;
import com.example.pogun.entity.community.CommunityPostReaction;
import com.example.pogun.entity.community.CommunityPostStatus;
import com.example.pogun.entity.community.CommunityPostVote;
import com.example.pogun.entity.user.User;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 CommunityService이다.
 */

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostVoteRepository communityPostVoteRepository;
    private final CommunityPostReactionRepository communityPostReactionRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    public CommunityPostListResponse getPostList(String type, String category, String tag, String q) {
        String normalizedCategory = normalizeFilter(category);
        String normalizedTag = normalizeFilter(tag);
        String normalizedQuery = normalizeFilter(q);

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if ("POPULAR".equalsIgnoreCase(type)) {
            sort = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }

        Page<CommunityPost> postPage = communityPostRepository.findPosts(
                CommunityPostStatus.ACTIVE,
                normalizedCategory,
                normalizedTag,
                normalizedQuery,
                PageRequest.of(0, 20, sort)
        );

        List<CommunityPostSummaryResponse> items = postPage.getContent().stream()
                .map(post -> new CommunityPostSummaryResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getCategory(),
                        List.copyOf(post.getTags()),
                        post.getAuthor().getNickname(),
                        post.getViewCount(),
                        post.getLikeCount(),
                        post.getCreatedAt()
                ))
                .toList();

        return new CommunityPostListResponse(postPage.getTotalElements(), postPage.getTotalPages(), items);
    }

    @Transactional
    public CommunityPostCreateResponse createPost(CommunityPostRequest request) {
        User author = getCurrentUser();

        CommunityPost post = CommunityPost.builder()
                .author(author)
                .title(request.getTitle())
                .content(request.getContent())
                .category(normalizeCategory(request.getCategory()))
                .status(CommunityPostStatus.ACTIVE)
                .pollQuestion(request.getPollQuestion())
                .pollOptions(request.getPollOptions())
                .build();

        post.getTags().addAll(normalizeTags(request.getTags()));

        if (request.getImageUrls() != null) {
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                post.getImages().add(CommunityPostImage.builder()
                        .post(post)
                        .imageUrl(request.getImageUrls().get(i))
                        .sortOrder(i)
                        .build());
            }
        }

        CommunityPost saved = communityPostRepository.save(post);
        return new CommunityPostCreateResponse(saved.getId(), saved.getTitle(), saved.getCategory(), List.copyOf(saved.getTags()));
    }

    // 상세 조회와 조회수 증가는 같은 트랜잭션에서 묶어 중복 저장 로직 없이 자연스럽게 누적되게 한다.
    @Transactional
    public CommunityPostDetailResponse getPostDetail(String postId) {
        CommunityPost post = getActivePost(postId);
        post.setViewCount(post.getViewCount() + 1);

        CommunityPollResponse poll = null;
        if (post.getPollQuestion() != null) {
            poll = new CommunityPollResponse(post.getPollQuestion(), post.getPollOptions());
        }

        return new CommunityPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                List.copyOf(post.getTags()),
                post.getAuthor().getNickname(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                poll,
                post.getImages().stream().map(CommunityPostImage::getImageUrl).toList()
        );
    }

    @Transactional
    public CommunityPostUpdateResponse updatePost(String postId, CommunityPostRequest request) {
        CommunityPost post = getPost(postId);

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("COMMUNITY_POST_FORBIDDEN", "수정 권한이 없습니다.");
        }

        if (request.getTitle() != null) post.setTitle(request.getTitle());
        if (request.getContent() != null) post.setContent(request.getContent());
        if (request.getCategory() != null) post.setCategory(normalizeCategory(request.getCategory()));
        if (request.getTags() != null) {
            post.getTags().clear();
            post.getTags().addAll(normalizeTags(request.getTags()));
        }

        if (request.getImageUrls() != null) {
            post.getImages().clear();
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                post.getImages().add(CommunityPostImage.builder()
                        .post(post)
                        .imageUrl(request.getImageUrls().get(i))
                        .sortOrder(i)
                        .build());
            }
        }

        return new CommunityPostUpdateResponse(post.getId(), true, post.getCategory(), List.copyOf(post.getTags()));
    }

    @Transactional
    public CommunityPostDeleteResponse deletePost(String postId) {
        CommunityPost post = getPost(postId);

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("COMMUNITY_POST_FORBIDDEN", "삭제 권한이 없습니다.");
        }

        communityPostRepository.delete(post);
        return new CommunityPostDeleteResponse(post.getId(), true);
    }

    public List<CommunityCommentResponse> getComments(String postId) {
        CommunityPost post = getActivePost(postId);

        List<CommunityComment> comments = communityCommentRepository.findByPostOrderByCreatedAtAsc(post);

        return comments.stream()
                .map(comment -> new CommunityCommentResponse(
                        comment.getId(),
                        comment.getContent(),
                        comment.getAuthor().getNickname(),
                        comment.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public CommunityCommentCreateResponse createComment(String postId, CommunityCommentRequest request) {
        CommunityPost post = getActivePost(postId);

        User author = getCurrentUser();

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .author(author)
                .content(request.getContent())
                .status(CommunityCommentStatus.NORMAL)
                .build();

        CommunityComment saved = communityCommentRepository.save(comment);

        return new CommunityCommentCreateResponse(saved.getId(), post.getId());
    }

    @Transactional
    public CommunityVoteResponse vote(String postId, CommunityVoteRequest request) {
        CommunityPost post = getActivePost(postId);
        User user = getCurrentUser();
        String selection = normalizeSelection(request.getOption());
        validatePollVote(post, selection);

        CommunityPostVote vote = communityPostVoteRepository.findByPostAndUser(post, user)
                .orElseGet(() -> CommunityPostVote.builder()
                        .post(post)
                        .user(user)
                        .build());
        vote.setSelectedOption(selection);
        communityPostVoteRepository.save(vote);

        return new CommunityVoteResponse(post.getId(), selection, "투표 참여 성공");
    }

    @Transactional
    public CommunityReactionResponse react(String postId, CommunityReactionRequest request) {
        CommunityPost post = getActivePost(postId);
        User user = getCurrentUser();
        String reaction = normalizeSelection(request.getReaction());

        CommunityPostReaction postReaction = communityPostReactionRepository.findByPostAndUser(post, user)
                .orElseGet(() -> CommunityPostReaction.builder()
                        .post(post)
                        .user(user)
                        .build());

        String previousReaction = postReaction.getReactionType();
        postReaction.setReactionType(reaction);
        communityPostReactionRepository.save(postReaction);
        updateLikeCount(post, previousReaction, reaction);

        return new CommunityReactionResponse(post.getId(), reaction, "좋아요/반응 처리 성공");
    }

    private CommunityPost getPost(String postId) {
        return communityPostRepository.findById(parseUuid(postId))
                .orElseThrow(() -> ApiException.notFound("COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다."));
    }

    private CommunityPost getActivePost(String postId) {
        CommunityPost post = getPost(postId);
        if (post.getStatus() != CommunityPostStatus.ACTIVE) {
            throw ApiException.notFound("COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
        return post;
    }

    private UUID parseUuid(String postId) {
        try {
            return UUID.fromString(postId);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_COMMUNITY_POST_ID", "올바르지 않은 게시글 ID 형식입니다.");
        }
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCategory(String category) {
        String normalized = normalizeFilter(category);
        return normalized == null ? "FREE" : normalized.toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizeSelection(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            throw ApiException.badRequest("INVALID_SELECTION", "선택 값은 필수입니다.");
        }
        return normalized;
    }

    private void validatePollVote(CommunityPost post, String selection) {
        if (post.getPollQuestion() == null || post.getPollOptions() == null || post.getPollOptions().isEmpty()) {
            throw ApiException.conflict("POLL_NOT_AVAILABLE", "이 게시글에는 투표가 없습니다.");
        }
        if (post.getPollEndsAt() != null && post.getPollEndsAt().isBefore(java.time.Instant.now())) {
            throw ApiException.conflict("POLL_CLOSED", "종료된 투표입니다.");
        }
        boolean allowed = post.getPollOptions().stream()
                .filter(option -> option != null)
                .map(option -> option.trim())
                .anyMatch(option -> option.equalsIgnoreCase(selection));
        if (!allowed) {
            throw ApiException.badRequest("INVALID_POLL_OPTION", "올바르지 않은 투표 옵션입니다.");
        }
    }

    private void updateLikeCount(CommunityPost post, String previousReaction, String currentReaction) {
        boolean wasLike = isLikeReaction(previousReaction);
        boolean isLike = isLikeReaction(currentReaction);
        if (wasLike == isLike) {
            return;
        }
        long nextLikeCount = post.getLikeCount() == null ? 0L : post.getLikeCount();
        nextLikeCount += isLike ? 1L : -1L;
        post.setLikeCount(Math.max(0L, nextLikeCount));
    }

    private boolean isLikeReaction(String reactionType) {
        return reactionType != null && reactionType.trim().equalsIgnoreCase("LIKE");
    }
}
