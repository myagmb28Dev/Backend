package com.example.demo.service;

import com.example.demo.entity.NoticeBookmark;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.repository.NoticeBookmarkRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final NoticeBookmarkRepository noticeBookmarkRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> addBookmark(String noticeId) {
        User user = getCurrentUser();
        PetNotice notice = getNotice(noticeId);

        NoticeBookmark bookmark = noticeBookmarkRepository.findByUserAndNotice(user, notice)
                .orElseGet(() -> noticeBookmarkRepository.save(NoticeBookmark.builder()
                        .user(user)
                        .notice(notice)
                        .build()));

        return Map.of(
                "bookmarkId", bookmark.getId(),
                "noticeId", notice.getId(),
                "bookmarked", true
        );
    }

    @Transactional
    public Map<String, Object> removeBookmark(String noticeId) {
        User user = getCurrentUser();
        PetNotice notice = getNotice(noticeId);
        noticeBookmarkRepository.findByUserAndNotice(user, notice)
                .ifPresent(noticeBookmarkRepository::delete);

        return Map.of("noticeId", notice.getId(), "bookmarked", false);
    }

    public List<Map<String, Object>> getMyBookmarks() {
        User user = getCurrentUser();
        return noticeBookmarkRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(bookmark -> toBookmarkSummary(bookmark.getNotice(), bookmark.getCreatedAt()))
                .toList();
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String noticeId) {
        try {
            return petNoticeRepository.findById(UUID.fromString(noticeId))
                    .orElseThrow(() -> new RuntimeException("공고를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private Map<String, Object> toBookmarkSummary(PetNotice notice, java.time.Instant bookmarkedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("noticeId", notice.getId());
        item.put("title", notice.getTitle());
        item.put("animalType", notice.getAnimalType());
        item.put("breed", notice.getBreed());
        item.put("missingDate", notice.getMissingDate());
        item.put("missingRegion", notice.getMissingRegion());
        item.put("status", notice.getStatus().name());
        item.put("statusLabel", getStatusLabel(notice));
        item.put("statusChanged", notice.getStatus() != com.example.demo.entity.enums.PetNoticeStatus.OPEN);
        item.put("bookmarked", true);
        item.put("bookmarkedAt", bookmarkedAt);
        return item;
    }

    private String getStatusLabel(PetNotice notice) {
        return switch (notice.getStatus()) {
            case OPEN -> "진행중";
            case RESOLVED -> "해결됨";
            case CLOSED -> "종료됨";
        };
    }
}
