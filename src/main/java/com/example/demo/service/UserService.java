package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> {
                    if ("test-uid-123".equals(firebaseUid)) {
                        return userRepository.save(User.builder()
                                .firebaseUid("test-uid-123")
                                .email("test@pogeun.com")
                                .nickname("테스트유저")
                                .role(com.example.demo.entity.enums.UserRole.USER)
                                .status(com.example.demo.entity.enums.UserStatus.ACTIVE)
                                .build());
                    }
                    throw new RuntimeException("사용자를 찾을 수 없습니다.");
                });
    }

    public Map<String, Object> getProfile() {
        User user = getCurrentUser();
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "https://cdn.ex.com/profile/default.png",
                "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                "role", user.getRole().name(),
                "status", user.getStatus().name()
        );
    }

    public Map<String, Object> updateProfile(Map<String, Object> request) {
        return request;
    }

    public List<Map<String, Object>> myPetNotices() {
        return List.of(
                Map.of("noticeId", "n1", "title", "강아지 찾습니다", "status", "OPEN"),
                Map.of("noticeId", "n2", "title", "고양이 발견", "status", "RESOLVED")
        );
    }

    public List<Map<String, Object>> myCommunityPosts() {
        return List.of(
                Map.of("postId", "p1", "title", "실종 경험 공유", "status", "ACTIVE"),
                Map.of("postId", "p2", "title", "목격 제보 부탁", "status", "ACTIVE")
        );
    }
}

