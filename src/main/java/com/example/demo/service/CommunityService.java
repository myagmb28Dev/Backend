package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityService {

    public Map<String, Object> getPostList(String type, String category, String tag, String q) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("type", type);
        filters.put("category", category);
        filters.put("tag", tag);
        filters.put("q", q);

        List<Map<String, Object>> items = List.of(
                Map.of("id", "post-1", "title", "강아지 찾았어요", "summary", "제보 감사합니다"),
                Map.of("id", "post-2", "title", "실종 전단지 팁", "summary", "지역 카페 공유 방법")
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("filters", filters);
        data.put("items", items);
        return data;
    }

    public Map<String, Object> createPost(Map<String, Object> request) {
        return Map.of("id", "post-new", "created", request);
    }

    public Map<String, Object> getPostDetail(String postId) {
        return Map.of(
                "id", postId,
                "title", "실종 전단지 팁",
                "content", "동네 카페와 SNS를 함께 활용하세요.",
                "poll", Map.of("question", "전단지 배포 경험", "options", List.of("있음", "없음"))
        );
    }

    public Map<String, Object> updatePost(String postId, Map<String, Object> request) {
        return Map.of("id", postId, "updated", request);
    }

    public Map<String, Object> deletePost(String postId) {
        return Map.of("id", postId, "deleted", true);
    }

    public List<Map<String, Object>> getComments(String postId) {
        return List.of(
                Map.of("id", "c1", "postId", postId, "content", "좋은 정보 감사합니다."),
                Map.of("id", "c2", "postId", postId, "content", "저도 같은 방법 썼어요.")
        );
    }

    public Map<String, Object> createComment(String postId, Map<String, String> request) {
        return Map.of("postId", postId, "content", request.get("content"));
    }

    public Map<String, Object> vote(String postId, Map<String, Object> request) {
        return Map.of("postId", postId, "selection", request.get("option"));
    }

    public Map<String, Object> react(String postId, Map<String, String> request) {
        return Map.of("postId", postId, "reaction", request.get("reaction"));
    }
}
