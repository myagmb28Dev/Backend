package com.example.pogun.service.community;

import com.example.pogun.dto.community.CommunityCommentCreateResponse;
import com.example.pogun.dto.community.CommunityCommentDeleteResponse;
import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentResponse;
import com.example.pogun.dto.community.CommunityCommentUpdateResponse;
import com.example.pogun.dto.community.CommunityPollResponse;
import com.example.pogun.dto.community.CommunityPostCreateResponse;
import com.example.pogun.dto.community.CommunityPostDeleteResponse;
import com.example.pogun.dto.community.CommunityPostDetailResponse;
import com.example.pogun.dto.community.CommunityPostListResponse;
import com.example.pogun.dto.community.CommunityPostRequest;
import com.example.pogun.dto.community.CommunityPostSummaryResponse;
import com.example.pogun.dto.community.CommunityPostUpdateRequest;
import com.example.pogun.dto.community.CommunityPostUpdateResponse;
import com.example.pogun.dto.community.CommunityReactionRequest;
import com.example.pogun.dto.community.CommunityReactionResponse;
import com.example.pogun.dto.community.CommunityVoteRequest;
import com.example.pogun.dto.community.CommunityVoteResponse;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostImage;
import com.example.pogun.entity.community.CommunityPostReaction;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.community.CommunityPostVote;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserFollow;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import com.example.pogun.repository.user.UserFollowRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.cache.AiSourceCacheService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.storage.S3ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityNotFoundException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 CommunityService이다.
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class CommunityService {
    private static final String SORT_LATEST = "LATEST";
    private static final String SORT_POPULAR = "POPULAR";
    private static final List<String> ALLOWED_CATEGORIES = List.of("FREE", "QUESTION", "TIP", "REVIEW", "NOTICE");
    private static final List<String> ALLOWED_REACTIONS = List.of("LIKE");
    private static final String CACHE_NAMESPACE = "community-posts";

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostVoteRepository communityPostVoteRepository;
    private final CommunityPostReactionRepository communityPostReactionRepository;
    private final S3ImageStorageService s3ImageStorageService;
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final NotificationService notificationService;
    private final AiSourceCacheService aiSourceCacheService;

    @Value("${app.ai-source-cache.ttl-seconds:60}")
    private long cacheTtlSeconds;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public CommunityPostListResponse getPostList(String type, String category, String tag, String q, int page, int size) {
        String normalizedType = normalizeSortType(type);
        String normalizedCategory = normalizeCategoryFilter(category);
        String normalizedTag = normalizeSearchFilter(tag);
        String normalizedQuery = normalizeSearchFilter(q);
        validatePageAndSize(page, size);

        String cacheKey = String.join(":",
                "public-list",
                "v" + aiSourceCacheService.currentVersion(CACHE_NAMESPACE),
                normalizedType,
                normalizedCategory,
                normalizedTag,
                normalizedQuery,
                String.valueOf(page),
                String.valueOf(size)
        );
        return aiSourceCacheService.getOrLoad(
                cacheKey,
                Duration.ofSeconds(cacheTtlSeconds),
                CommunityPostListResponse.class,
                () -> getPostListUncached(normalizedType, normalizedCategory, normalizedTag, normalizedQuery, page, size)
        );
    }

    private CommunityPostListResponse getPostListUncached(String normalizedType, String normalizedCategory, String normalizedTag, String normalizedQuery, int page, int size) {

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (SORT_POPULAR.equals(normalizedType)) {
            sort = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }

        Page<CommunityPost> postPage = communityPostRepository.findPosts(
                CommunityPostStatus.ACTIVE,
                normalizedCategory,
                normalizedTag,
                normalizedQuery,
                PageRequest.of(page, size, sort)
        );

        List<CommunityPostSummaryResponse> items = postPage.getContent().stream()
                .map(post -> new CommunityPostSummaryResponse(
                        post.getId(),
                        post.getTitle(),
                        resolveThumbnailImageUrl(post),
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

    @Transactional(readOnly = true)
    public CommunityPostListResponse searchPosts(String query, String category, String type, int page, int size) {
        String normalizedQuery = normalizeSearchFilter(query);
        if (normalizedQuery.isBlank()) {
            throw ApiException.badRequest("MISSING_SEARCH_QUERY", "검색어는 필수입니다.");
        }
        String normalizedType = normalizeSortType(type);
        String normalizedCategory = normalizeCategoryFilter(category);
        validatePageAndSize(page, size);

        String cacheKey = String.join(":",
                "search",
                "v" + aiSourceCacheService.currentVersion(CACHE_NAMESPACE),
                normalizedType,
                normalizedCategory,
                normalizedQuery,
                String.valueOf(page),
                String.valueOf(size)
        );
        return aiSourceCacheService.getOrLoad(
                cacheKey,
                Duration.ofSeconds(cacheTtlSeconds),
                CommunityPostListResponse.class,
                () -> searchPostsUncached(normalizedQuery, normalizedCategory, normalizedType, page, size)
        );
    }

    private CommunityPostListResponse searchPostsUncached(String query, String category, String type, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (SORT_POPULAR.equals(type)) {
            sort = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }

        Page<CommunityPost> postPage = communityPostRepository.searchPosts(
                CommunityPostStatus.ACTIVE,
                category,
                query,
                PageRequest.of(page, size, sort)
        );

        List<CommunityPostSummaryResponse> items = postPage.getContent().stream()
                .map(post -> new CommunityPostSummaryResponse(
                        post.getId(),
                        post.getTitle(),
                        resolveThumbnailImageUrl(post),
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
        return createPost(request, List.of());
    }

    @Transactional
    public CommunityPostCreateResponse createPost(CommunityPostRequest request, List<MultipartFile> imageFiles) {
        User author = getCurrentUser();

        List<String> pollOptions = normalizePollOptions(request.getPollOptions());
        CommunityPost post = CommunityPost.builder()
                .author(author)
                .title(request.getTitle())
                .content(request.getContent())
                .category(normalizeCategory(request.getCategory()))
                .status(CommunityPostStatus.ACTIVE)
                .build();

        post.getTags().addAll(normalizeTags(request.getTags()));
        applyPoll(post, request.getPollQuestion(), pollOptions, false);

        attachImages(author.getId(), post, imageFiles, true);

        CommunityPost saved = communityPostRepository.save(post);
        notifyCommunityPostCreated(saved, author);
        aiSourceCacheService.bumpVersion(CACHE_NAMESPACE);
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
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.getAuthor().getProfileImageUrl(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                poll,
                post.getImages().stream().map(CommunityPostImage::getImageUrl).toList()
        );
    }

    @Transactional
    public CommunityPostUpdateResponse updatePost(String postId, CommunityPostUpdateRequest request) {
        return updatePost(postId, request, List.of());
    }

    @Transactional
    public CommunityPostUpdateResponse updatePost(String postId, CommunityPostUpdateRequest request, List<MultipartFile> imageFiles) {
        CommunityPost post = getActivePost(postId);

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

        applyPoll(post, request.getPollQuestion(), request.getPollOptions(), true);

        attachImages(post.getAuthor().getId(), post, imageFiles, imageFiles != null && !imageFiles.isEmpty());

        aiSourceCacheService.bumpVersion(CACHE_NAMESPACE);
        return new CommunityPostUpdateResponse(post.getId(), true, post.getCategory(), List.copyOf(post.getTags()));
    }

    @Transactional
    public CommunityPostDeleteResponse deletePost(String postId) {
        CommunityPost post = getPost(postId);

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("COMMUNITY_POST_FORBIDDEN", "삭제 권한이 없습니다.");
        }

        post.setStatus(CommunityPostStatus.DELETED);
        softDeleteCommentsForPost(post);
        communityPostRepository.save(post);
        aiSourceCacheService.bumpVersion(CACHE_NAMESPACE);
        return new CommunityPostDeleteResponse(post.getId(), true);
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponse> getComments(String postId) {
        CommunityPost post = getActivePost(postId);

        List<CommunityComment> comments = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL);
        return buildCommentTree(comments);
    }

    @Transactional
    public CommunityCommentCreateResponse createComment(String postId, CommunityCommentRequest request) {
        CommunityPost post = getActivePost(postId);

        User author = getCurrentUser();
        CommunityComment parentComment = null;
        if (request.getParentCommentId() != null) {
            parentComment = getActiveComment(post, request.getParentCommentId());
        }

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .author(author)
                .parentComment(parentComment)
                .content(request.getContent())
                .status(CommunityCommentStatus.NORMAL)
                .build();

        CommunityComment saved = communityCommentRepository.save(comment);
        notifyCommentCreated(saved, author);

        return new CommunityCommentCreateResponse(saved.getId(), post.getId(), parentComment == null ? null : parentComment.getId());
    }

    @Transactional
    public CommunityCommentDeleteResponse deleteComment(String postId, String commentId) {
        CommunityPost post = getActivePost(postId);
        CommunityComment comment = getActiveComment(post, parseUuid(commentId));

        User currentUser = getCurrentUser();
        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("COMMUNITY_COMMENT_FORBIDDEN", "댓글 삭제 권한이 없습니다.");
        }

        softDeleteCommentTree(post, comment);

        return new CommunityCommentDeleteResponse(comment.getId(), post.getId(), true);
    }

    @Transactional
    public CommunityCommentUpdateResponse updateComment(String postId, String commentId, CommunityCommentRequest request) {
        CommunityPost post = getActivePost(postId);
        CommunityComment comment = getActiveComment(post, parseUuid(commentId));

        User currentUser = getCurrentUser();
        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("COMMUNITY_COMMENT_FORBIDDEN", "댓글 수정 권한이 없습니다.");
        }

        comment.setContent(request.getContent());
        communityCommentRepository.save(comment);
        return new CommunityCommentUpdateResponse(comment.getId(), post.getId(), true);
    }

    @Transactional
    public CommunityVoteResponse vote(String postId, CommunityVoteRequest request) {
        CommunityPost post = getActivePost(postId);
        User user = getCurrentUser();
        String selection = normalizeVoteSelection(request.getOption());
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
        String reaction = normalizeReaction(request.getReaction());

        CommunityPostReaction postReaction = communityPostReactionRepository.findByPostAndUser(post, user)
                .orElseGet(() -> CommunityPostReaction.builder()
                        .post(post)
                        .user(user)
                        .build());

        String previousReaction = postReaction.getReactionType();
        postReaction.setReactionType(reaction);
        communityPostReactionRepository.save(postReaction);
        long likeCountDelta = resolveLikeCountDelta(previousReaction, reaction);
        if (likeCountDelta != 0L) {
            communityPostRepository.adjustLikeCount(post.getId(), likeCountDelta);
            aiSourceCacheService.bumpVersion(CACHE_NAMESPACE);
        }
        if (likeCountDelta > 0) {
            notifyPostLiked(post, user);
        }

        return new CommunityReactionResponse(post.getId(), reaction, "좋아요/반응 처리 성공");
    }

    @Transactional
    public CommunityReactionResponse unlike(String postId) {
        CommunityPost post = getActivePost(postId);
        User user = getCurrentUser();

        CommunityPostReaction postReaction = communityPostReactionRepository.findByPostAndUser(post, user).orElse(null);
        if (postReaction == null) {
            return new CommunityReactionResponse(post.getId(), "NONE", "좋아요 취소 처리 성공");
        }

        long likeCountDelta = resolveLikeCountDelta(postReaction.getReactionType(), "NONE");
        if (likeCountDelta != 0L) {
            communityPostRepository.adjustLikeCount(post.getId(), likeCountDelta);
            aiSourceCacheService.bumpVersion(CACHE_NAMESPACE);
        }
        communityPostReactionRepository.delete(postReaction);
        return new CommunityReactionResponse(post.getId(), "NONE", "좋아요 취소 처리 성공");
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

    private String normalizeSearchFilter(String value) {
        String normalized = normalizeFilter(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return "FREE";
        }
        String normalized = normalizeFilter(category);
        if (normalized == null) {
            throw ApiException.badRequest("INVALID_CATEGORY", "카테고리는 비어 있을 수 없습니다.");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(upper)) {
            throw ApiException.badRequest("INVALID_CATEGORY", "지원하지 않는 카테고리입니다.");
        }
        return upper;
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

    private String normalizeVoteSelection(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            throw ApiException.badRequest("INVALID_SELECTION", "선택 값은 필수입니다.");
        }
        return normalized;
    }

    private String normalizeReaction(String value) {
        String normalized = normalizeVoteSelection(value);
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_REACTIONS.contains(upper)) {
            throw ApiException.badRequest("INVALID_REACTION", "지원하지 않는 반응 타입입니다.");
        }
        return upper;
    }

    private String normalizeSortType(String type) {
        if (type == null) {
            return SORT_LATEST;
        }
        String normalized = normalizeFilter(type);
        if (normalized == null) {
            throw ApiException.badRequest("INVALID_SORT_TYPE", "정렬 타입은 비어 있을 수 없습니다.");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!SORT_LATEST.equals(upper) && !SORT_POPULAR.equals(upper)) {
            throw ApiException.badRequest("INVALID_SORT_TYPE", "정렬 타입은 LATEST 또는 POPULAR만 지원합니다.");
        }
        return upper;
    }

    private String normalizeCategoryFilter(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "";
        }
        String normalized = normalizeFilter(category);
        if (normalized == null) {
            return "";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(upper)) {
            throw ApiException.badRequest("INVALID_CATEGORY", "지원하지 않는 카테고리입니다.");
        }
        return upper.toLowerCase(Locale.ROOT);
    }

    private void validatePageAndSize(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("INVALID_SIZE", "size는 1 이상 100 이하여야 합니다.");
        }
    }

    private String resolveThumbnailImageUrl(CommunityPost post) {
        if (post.getImages() == null || post.getImages().isEmpty()) {
            return null;
        }
        return post.getImages().get(0).getImageUrl();
    }

    private List<String> normalizePollOptions(List<String> pollOptions) {
        if (pollOptions == null) {
            return null;
        }
        return pollOptions.stream()
                .filter(option -> option != null && !option.isBlank())
                .map(option -> option.trim())
                .toList();
    }

    private void applyPoll(CommunityPost post, String pollQuestion, List<String> pollOptions, boolean allowPartialUpdate) {
        boolean questionProvided = pollQuestion != null;
        boolean optionsProvided = pollOptions != null;

        if (!questionProvided && !optionsProvided) {
            if (allowPartialUpdate) {
                return;
            }
            if (post.getPollQuestion() == null && (post.getPollOptions() == null || post.getPollOptions().isEmpty())) {
                return;
            }
        }

        if (questionProvided ^ optionsProvided) {
            throw ApiException.badRequest("INVALID_POLL_REQUEST", "투표 질문과 옵션은 함께 제공되어야 합니다.");
        }

        String normalizedQuestion = normalizeFilter(pollQuestion);
        if (normalizedQuestion == null) {
            throw ApiException.badRequest("INVALID_POLL_QUESTION", "투표 질문은 비어 있을 수 없습니다.");
        }

        List<String> normalizedOptions = normalizePollOptions(pollOptions);
        if (normalizedOptions == null || normalizedOptions.size() < 2) {
            throw ApiException.badRequest("INVALID_POLL_OPTIONS", "투표 옵션은 2개 이상이어야 합니다.");
        }

        post.setPollQuestion(normalizedQuestion);
        post.setPollOptions(new ArrayList<>(normalizedOptions));
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

    private long resolveLikeCountDelta(String previousReaction, String currentReaction) {
        boolean wasLike = isLikeReaction(previousReaction);
        boolean isLike = isLikeReaction(currentReaction);
        if (wasLike == isLike) {
            return 0L;
        }
        return isLike ? 1L : -1L;
    }

    private boolean isLikeReaction(String reactionType) {
        return reactionType != null && reactionType.trim().equalsIgnoreCase("LIKE");
    }

    private void notifyCommunityPostCreated(CommunityPost post, User author) {
        Map<String, String> metadata = Map.of(
                "postId", post.getId().toString(),
                "category", post.getCategory()
        );
        for (UserFollow follow : userFollowRepository.findByFollowingOrderByCreatedAtDesc(author)) {
            safeCreateAndSendNotification(
                    follow.getFollower(),
                    author,
                    NotificationType.FOLLOWING_POST,
                    NotificationTargetType.COMMUNITY_POST,
                    post.getId(),
                    author.getNickname() + "님이 새 글을 작성했습니다.",
                    post.getTitle(),
                    NotificationPriority.NORMAL,
                    "following-post:" + follow.getFollower().getId() + ":" + post.getId(),
                    metadata
            );
        }
        if ("NOTICE".equalsIgnoreCase(post.getCategory())) {
            for (User user : userRepository.findAll()) {
                safeCreateAndSendNotification(
                        user,
                        author,
                        NotificationType.COMMUNITY_NOTICE,
                        NotificationTargetType.COMMUNITY_POST,
                        post.getId(),
                        "커뮤니티 공지",
                        post.getTitle(),
                        NotificationPriority.HIGH,
                        "community-notice:" + user.getId() + ":" + post.getId(),
                        metadata
                );
            }
        }
    }

    private void notifyCommentCreated(CommunityComment comment, User actor) {
        CommunityPost post = comment.getPost();
        Map<String, String> metadata = Map.of(
                "postId", post.getId().toString(),
                "commentId", comment.getId().toString()
        );
        safeCreateAndSendNotification(
                post.getAuthor(),
                actor,
                NotificationType.COMMUNITY_POST_COMMENT,
                NotificationTargetType.COMMUNITY_COMMENT,
                comment.getId(),
                "내 글에 댓글이 달렸습니다.",
                post.getTitle() + " 글에 댓글이 달렸습니다.",
                NotificationPriority.NORMAL,
                "community-post-comment:" + post.getAuthor().getId() + ":" + comment.getId(),
                metadata
        );
        CommunityComment parentComment = comment.getParentComment();
        if (parentComment != null) {
            safeCreateAndSendNotification(
                    parentComment.getAuthor(),
                    actor,
                    NotificationType.COMMUNITY_COMMENT_REPLY,
                    NotificationTargetType.COMMUNITY_COMMENT,
                    comment.getId(),
                    "내 댓글에 답글이 달렸습니다.",
                    post.getTitle() + " 글의 댓글에 답글이 달렸습니다.",
                    NotificationPriority.NORMAL,
                    "community-comment-reply:" + parentComment.getAuthor().getId() + ":" + comment.getId(),
                    metadata
            );
        }
    }

    private void notifyPostLiked(CommunityPost post, User actor) {
        safeCreateAndSendNotification(
                post.getAuthor(),
                actor,
                NotificationType.COMMUNITY_POST_LIKE,
                NotificationTargetType.COMMUNITY_POST,
                post.getId(),
                "내 글을 좋아합니다.",
                actor.getNickname() + "님이 " + post.getTitle() + " 글을 좋아합니다.",
                NotificationPriority.NORMAL,
                "community-post-like:" + post.getAuthor().getId() + ":" + actor.getId() + ":" + post.getId(),
                Map.of("postId", post.getId().toString())
        );
    }

    private void safeCreateAndSendNotification(
            User user,
            User actor,
            NotificationType type,
            NotificationTargetType targetType,
            UUID targetId,
            String title,
            String body,
            NotificationPriority priority,
            String dedupeKey,
            Map<String, String> metadata
    ) {
        try {
            notificationService.createAndSendNotification(
                    user,
                    actor,
                    type,
                    targetType,
                    targetId,
                    title,
                    body,
                    priority,
                    dedupeKey,
                    metadata
            );
        } catch (Exception ex) {
            log.warn(
                    "Community notification failed. type={}, targetType={}, targetId={}, userId={}, reason={}",
                    type,
                    targetType,
                    targetId,
                    user != null ? user.getId() : null,
                    ex.getMessage()
            );
        }
    }

    private void softDeleteCommentsForPost(CommunityPost post) {
        List<CommunityComment> comments = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL);
        comments.forEach(comment -> comment.setStatus(CommunityCommentStatus.DELETED));
    }

    private void attachImages(UUID ownerId, CommunityPost post, List<MultipartFile> imageFiles, boolean shouldReplaceImages) {
        if (!shouldReplaceImages) {
            return;
        }

        post.getImages().clear();

        List<String> resolvedImageUrls = new ArrayList<>();
        if (imageFiles != null && !imageFiles.isEmpty()) {
            resolvedImageUrls.addAll(s3ImageStorageService.storeImages("community", "posts", ownerId, imageFiles));
        }

        for (int i = 0; i < resolvedImageUrls.size(); i++) {
            post.getImages().add(CommunityPostImage.builder()
                    .post(post)
                    .imageUrl(resolvedImageUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
    }

    private CommunityComment getActiveComment(CommunityPost post, UUID commentId) {
        CommunityComment comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("COMMUNITY_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
        if (!comment.getPost().getId().equals(post.getId()) || comment.getStatus() != CommunityCommentStatus.NORMAL) {
            throw ApiException.notFound("COMMUNITY_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
        }
        return comment;
    }

    private List<CommunityCommentResponse> buildCommentTree(List<CommunityComment> comments) {
        Map<UUID, CommentNode> nodesById = new LinkedHashMap<>();
        for (CommunityComment comment : comments) {
            nodesById.put(comment.getId(), new CommentNode(toCommentResponse(comment)));
        }

        List<CommentNode> roots = new ArrayList<>();
        for (CommunityComment comment : comments) {
            CommentNode node = nodesById.get(comment.getId());
            UUID parentId = safeParentCommentId(comment);
            if (parentId != null && !parentId.equals(comment.getId())) {
                CommentNode parent = nodesById.get(parentId);
                if (parent != null) {
                    parent.children.add(node);
                } else {
                    roots.add(node);
                }
            } else {
                roots.add(node);
            }
        }

        return roots.stream()
                .map(root -> root.toResponse(new java.util.HashSet<>()))
                .toList();
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment) {
        String authorNickname = safeAuthorNickname(comment);
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getContent(),
                authorNickname,
                comment.getCreatedAt(),
                safeParentCommentId(comment),
                List.of()
        );
    }

    private String safeAuthorNickname(CommunityComment comment) {
        try {
            if (comment.getAuthor() == null) {
                return "(탈퇴한 사용자)";
            }
            String nickname = comment.getAuthor().getNickname();
            return nickname == null || nickname.isBlank() ? "(탈퇴한 사용자)" : nickname;
        } catch (EntityNotFoundException ex) {
            log.warn("Community comment author missing. commentId={}", comment.getId());
            return "(탈퇴한 사용자)";
        }
    }

    private UUID safeParentCommentId(CommunityComment comment) {
        try {
            if (comment.getParentComment() == null) {
                return null;
            }
            return comment.getParentComment().getId();
        } catch (EntityNotFoundException ex) {
            log.warn("Community comment parent missing. commentId={}", comment.getId());
            return null;
        }
    }

    private void softDeleteCommentTree(CommunityPost post, CommunityComment rootComment) {
        List<CommunityComment> comments = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL);
        Map<UUID, List<CommunityComment>> childrenByParentId = new LinkedHashMap<>();
        for (CommunityComment comment : comments) {
            if (comment.getParentComment() == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(comment.getParentComment().getId(), key -> new ArrayList<>()).add(comment);
        }
        markCommentTreeDeleted(rootComment, childrenByParentId);
    }

    private void markCommentTreeDeleted(CommunityComment comment, Map<UUID, List<CommunityComment>> childrenByParentId) {
        comment.setStatus(CommunityCommentStatus.DELETED);
        for (CommunityComment child : childrenByParentId.getOrDefault(comment.getId(), List.of())) {
            markCommentTreeDeleted(child, childrenByParentId);
        }
    }

    private record CommentNode(CommunityCommentResponse response, List<CommentNode> children) {
        private CommentNode(CommunityCommentResponse response) {
            this(response, new ArrayList<>());
        }

        private CommunityCommentResponse toResponse(java.util.Set<UUID> path) {
            if (response.id() != null && !path.add(response.id())) {
                return new CommunityCommentResponse(
                        response.id(),
                        response.content(),
                        response.authorNickname(),
                        response.createdAt(),
                        response.parentCommentId(),
                        List.of()
                );
            }
            List<CommunityCommentResponse> childResponses = children.stream()
                    .map(child -> child.toResponse(new java.util.HashSet<>(path)))
                    .toList();
            return new CommunityCommentResponse(
                    response.id(),
                    response.content(),
                    response.authorNickname(),
                    response.createdAt(),
                    response.parentCommentId(),
                    childResponses
            );
        }
    }
}






