package com.example.demo.service;

import com.example.demo.dto.CommunityCommentRequest;
import com.example.demo.dto.CommunityPostRequest;
import com.example.demo.entity.CommunityComment;
import com.example.demo.entity.CommunityPost;
import com.example.demo.entity.CommunityPostImage;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.CommunityCommentStatus;
import com.example.demo.entity.enums.CommunityPostStatus;
import com.example.demo.repository.CommunityCommentRepository;
import com.example.demo.repository.CommunityPostRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    public Map<String, Object> getPostList(String type, String category, String tag, String q) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if ("POPULAR".equalsIgnoreCase(type)) {
            sort = Sort.by(Sort.Direction.DESC, "viewCount");
        }

        Page<CommunityPost> postPage = communityPostRepository.findPosts(CommunityPostStatus.ACTIVE, q, PageRequest.of(0, 20, sort));

        List<Map<String, Object>> items = postPage.getContent().stream().map(post -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", post.getId());
            item.put("title", post.getTitle());
            item.put("authorNickname", post.getAuthor().getNickname());
            item.put("viewCount", post.getViewCount());
            item.put("createdAt", post.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalElements", postPage.getTotalElements());
        data.put("totalPages", postPage.getTotalPages());
        data.put("items", items);
        return data;
    }

    @Transactional
    public Map<String, Object> createPost(CommunityPostRequest request) {
        User author = getCurrentUser();

        CommunityPost post = CommunityPost.builder()
                .author(author)
                .title(request.getTitle())
                .content(request.getContent())
                .status(CommunityPostStatus.ACTIVE)
                .pollQuestion(request.getPollQuestion())
                .pollOptions(request.getPollOptions())
                .build();

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

        return Map.of("id", saved.getId(), "title", saved.getTitle());
    }

    @Transactional
    public Map<String, Object> getPostDetail(String postId) {
        CommunityPost post = communityPostRepository.findById(UUID.fromString(postId))
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        post.setViewCount(post.getViewCount() + 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", post.getId());
        data.put("title", post.getTitle());
        data.put("content", post.getContent());
        data.put("authorNickname", post.getAuthor().getNickname());
        data.put("viewCount", post.getViewCount());
        data.put("createdAt", post.getCreatedAt());

        if (post.getPollQuestion() != null) {
            data.put("poll", Map.of(
                    "question", post.getPollQuestion(),
                    "options", post.getPollOptions()
            ));
        }

        data.put("imageUrls", post.getImages().stream()
                .map(CommunityPostImage::getImageUrl)
                .collect(Collectors.toList()));

        return data;
    }

    @Transactional
    public Map<String, Object> updatePost(String postId, CommunityPostRequest request) {
        CommunityPost post = communityPostRepository.findById(UUID.fromString(postId))
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        if (request.getTitle() != null) post.setTitle(request.getTitle());
        if (request.getContent() != null) post.setContent(request.getContent());

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

        return Map.of("id", post.getId(), "updated", true);
    }

    @Transactional
    public Map<String, Object> deletePost(String postId) {
        CommunityPost post = communityPostRepository.findById(UUID.fromString(postId))
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new RuntimeException("삭제 권한이 없습니다.");
        }

        communityPostRepository.delete(post);
        return Map.of("id", postId, "deleted", true);
    }

    public List<Map<String, Object>> getComments(String postId) {
        CommunityPost post = communityPostRepository.findById(UUID.fromString(postId))
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        List<CommunityComment> comments = communityCommentRepository.findByPostOrderByCreatedAtAsc(post);

        return comments.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("content", c.getContent());
            map.put("authorNickname", c.getAuthor().getNickname());
            map.put("createdAt", c.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createComment(String postId, CommunityCommentRequest request) {
        CommunityPost post = communityPostRepository.findById(UUID.fromString(postId))
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        User author = getCurrentUser();

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .author(author)
                .content(request.getContent())
                .status(CommunityCommentStatus.NORMAL)
                .build();

        CommunityComment saved = communityCommentRepository.save(comment);

        return Map.of("id", saved.getId(), "postId", postId);
    }

    public Map<String, Object> vote(String postId, Map<String, Object> request) {
        return Map.of("postId", postId, "selection", request.get("option"), "message", "투표 기능은 추후 확장 예정입니다.");
    }

    public Map<String, Object> react(String postId, Map<String, String> request) {
        return Map.of("postId", postId, "reaction", request.get("reaction"), "message", "반응 기능은 추후 확장 예정입니다.");
    }
}
