package com.example.pogun.service.bookmark;

import com.example.pogun.dto.bookmark.BookmarkActionResponse;
import com.example.pogun.dto.bookmark.BookmarkSummaryResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.bookmark.NoticeBookmark;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 BookmarkService이다.
 */

@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final NoticeBookmarkRepository noticeBookmarkRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;

    // 즐겨찾기 추가는 idempotent 하게 동작해서 같은 공고를 다시 눌러도 기존 북마크를 재사용한다.
    @Transactional
    public BookmarkActionResponse addBookmark(String noticeId) {
        User user = getCurrentUser();
        PetNotice notice = getNotice(noticeId);

        NoticeBookmark bookmark = noticeBookmarkRepository.findByUserAndNotice(user, notice)
                .orElseGet(() -> noticeBookmarkRepository.save(NoticeBookmark.builder()
                        .user(user)
                        .notice(notice)
                        .build()));

        return new BookmarkActionResponse(bookmark.getId(), notice.getId(), true);
    }

    @Transactional
    public BookmarkActionResponse removeBookmark(String noticeId) {
        User user = getCurrentUser();
        PetNotice notice = getNotice(noticeId);
        noticeBookmarkRepository.findByUserAndNotice(user, notice)
                .ifPresent(noticeBookmarkRepository::delete);

        return new BookmarkActionResponse(null, notice.getId(), false);
    }

    public List<BookmarkSummaryResponse> getMyBookmarks() {
        User user = getCurrentUser();
        return noticeBookmarkRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(bookmark -> toBookmarkSummary(bookmark.getNotice(), bookmark.getCreatedAt()))
                .toList();
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String noticeId) {
        try {
            return petNoticeRepository.findById(UUID.fromString(noticeId))
                    .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "공고를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.");
        }
    }

    // 목록 카드는 메인 공고 카드와 같은 상태 표기를 재사용해 프론트 표현이 어긋나지 않게 맞춘다.
    private BookmarkSummaryResponse toBookmarkSummary(PetNotice notice, Instant bookmarkedAt) {
        return new BookmarkSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAnimalType(),
                notice.getBreed(),
                notice.getMissingDate(),
                notice.getMissingRegion(),
                notice.getStatus().name(),
                getStatusLabel(notice),
                notice.getStatus() != com.example.pogun.entity.missingpet.PetNoticeStatus.OPEN,
                true,
                bookmarkedAt
        );
    }

    private String getStatusLabel(PetNotice notice) {
        return switch (notice.getStatus()) {
            case OPEN -> "진행중";
            case RESOLVED -> "해결됨";
            case CLOSED -> "종료됨";
        };
    }
}