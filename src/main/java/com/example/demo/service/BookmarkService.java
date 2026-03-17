package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    public Map<String, Object> addBookmark(String noticeId) {
        return Map.of("noticeId", noticeId, "bookmarked", true);
    }

    public Map<String, Object> removeBookmark(String noticeId) {
        return Map.of("noticeId", noticeId, "bookmarked", false);
    }

    public List<Map<String, Object>> getMyBookmarks() {
        return List.of(
                Map.of("noticeId", "notice-1", "title", "말티즈 실종"),
                Map.of("noticeId", "notice-7", "title", "고양이 목격")
        );
    }
}
