package com.example.demo.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    public Map<String, Object> getProfile() {
        return Map.of(
                "id", "b3367f11-8a16-4f53-a669-8629f05c9821",
                "email", "user@example.com",
                "nickname", "포군유저",
                "profileImageUrl", "https://cdn.ex.com/profile/default.png",
                "phoneNumber", "010-1234-5678",
                "role", "USER",
                "status", "ACTIVE"
        );
    }

    public Map<String, Object> updateProfile(Map<String, Object> request) {
        // 현재는 요청 payload를 그대로 반환하는 스텁입니다.
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

