package com.example.demo.service;

import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.entity.CommunityPost;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.UserSocialAccount;
import com.example.demo.repository.CommunityPostRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    public Map<String, Object> getProfile() {
        User user = getCurrentUser();
        return getProfileResponse(user);
    }

    public Map<String, Object> updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl().trim());
        }

        User saved = userRepository.save(user);
        return getProfileResponse(saved);
    }

    public List<Map<String, Object>> myPetNotices() {
        User user = getCurrentUser();
        return petNoticeRepository.findByAuthorOrderByCreatedAtDesc(user).stream()
                .map(this::toPetNoticeSummary)
                .toList();
    }

    public List<Map<String, Object>> myCommunityPosts() {
        User user = getCurrentUser();
        return communityPostRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toCommunityPostSummary)
                .toList();
    }

    private Map<String, Object> getProfileResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "https://cdn.ex.com/profile/default.png",
                "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                "provider", user.getAuthProvider() != null ? user.getAuthProvider() : "GOOGLE",
                "linkedProviders", getLinkedProviders(user),
                "role", user.getRole().name(),
                "status", user.getStatus().name()
        );
    }

    private List<String> getLinkedProviders(User user) {
        List<String> linkedProviders = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user).stream()
                .map(UserSocialAccount::getProvider)
                .toList();
        if (!linkedProviders.isEmpty()) {
            return linkedProviders;
        }
        if (user.getAuthProvider() != null && !user.getAuthProvider().isBlank()) {
            return List.of(user.getAuthProvider());
        }
        return List.of();
    }

    private Map<String, Object> toPetNoticeSummary(PetNotice notice) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("noticeId", notice.getId());
        item.put("title", notice.getTitle());
        item.put("animalType", notice.getAnimalType());
        item.put("breed", notice.getBreed());
        item.put("missingDate", notice.getMissingDate());
        item.put("missingRegion", notice.getMissingRegion());
        item.put("status", notice.getStatus().name());
        item.put("viewCount", notice.getViewCount());
        item.put("createdAt", notice.getCreatedAt());
        return item;
    }

    private Map<String, Object> toCommunityPostSummary(CommunityPost post) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("postId", post.getId());
        item.put("title", post.getTitle());
        item.put("status", post.getStatus().name());
        item.put("viewCount", post.getViewCount());
        item.put("createdAt", post.getCreatedAt());
        return item;
    }
}

