package com.example.pogun.service.community;

import com.example.pogun.dto.community.CommunityCommentCreateResponse;
import com.example.pogun.dto.community.CommunityCommentDeleteResponse;
import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentResponse;
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

    public CommunityPostListResponse getPostList(String type, String category, String tag, String q, int page, int size) {
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
                PageRequest.of(Math.max(page, 0), clampSize(size), sort)
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
    public CommunityPostUpdateResponse updatePost(String postId, CommunityPostUpdateRequest request) {
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

        applyPoll(post, request.getPollQuestion(), request.getPollOptions(), true);

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

        post.setStatus(CommunityPostStatus.DELETED);
        softDeleteCommentsForPost(post);
        communityPostRepository.save(post);
        return new CommunityPostDeleteResponse(post.getId(), true);
    }

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

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
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

    private void softDeleteCommentsForPost(CommunityPost post) {
        List<CommunityComment> comments = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL);
        comments.forEach(comment -> comment.setStatus(CommunityCommentStatus.DELETED));
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
            UUID parentId = comment.getParentComment() == null ? null : comment.getParentComment().getId();
            if (parentId != null) {
                CommentNode parent = nodesById.get(parentId);
                if (parent != null) {
                    parent.children.add(node);
                }
            } else {
                roots.add(node);
            }
        }

        return roots.stream()
                .map(CommentNode::toResponse)
                .toList();
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment) {
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getNickname(),
                comment.getCreatedAt(),
                comment.getParentComment() == null ? null : comment.getParentComment().getId(),
                List.of()
        );
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

        private CommunityCommentResponse toResponse() {
            List<CommunityCommentResponse> childResponses = children.stream()
                    .map(CommentNode::toResponse)
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






