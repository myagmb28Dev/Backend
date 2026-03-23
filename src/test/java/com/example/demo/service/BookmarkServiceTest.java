package com.example.demo.service;

import com.example.demo.entity.NoticeBookmark;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.PetGender;
import com.example.demo.entity.enums.PetNoticeStatus;
import com.example.demo.entity.enums.UserRole;
import com.example.demo.entity.enums.UserStatus;
import com.example.demo.repository.NoticeBookmarkRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private NoticeBookmarkRepository noticeBookmarkRepository;

    @Mock
    private PetNoticeRepository petNoticeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookmarkService bookmarkService;

    private User user;
    private PetNotice notice;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("user@example.com")
                .nickname("유저")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("말티즈를 찾습니다")
                .animalType("DOG")
                .breed("말티즈")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(0L)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid-1", null)
        );

        given(userRepository.findByFirebaseUid("firebase-uid-1")).willReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("즐겨찾기 추가 시 새 북마크를 저장한다")
    void addBookmark_savesNewBookmark() {
        NoticeBookmark bookmark = NoticeBookmark.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notice(notice)
                .build();
        given(petNoticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        given(noticeBookmarkRepository.findByUserAndNotice(user, notice)).willReturn(Optional.empty());
        given(noticeBookmarkRepository.save(any(NoticeBookmark.class))).willReturn(bookmark);

        Map<String, Object> result = bookmarkService.addBookmark(notice.getId().toString());

        assertThat(result.get("noticeId")).isEqualTo(notice.getId());
        assertThat(result.get("bookmarked")).isEqualTo(true);
        verify(noticeBookmarkRepository).save(any(NoticeBookmark.class));
    }

    @Test
    @DisplayName("즐겨찾기 해제 시 기존 북마크를 삭제한다")
    void removeBookmark_deletesExistingBookmark() {
        NoticeBookmark bookmark = NoticeBookmark.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notice(notice)
                .build();
        given(petNoticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        given(noticeBookmarkRepository.findByUserAndNotice(user, notice)).willReturn(Optional.of(bookmark));

        Map<String, Object> result = bookmarkService.removeBookmark(notice.getId().toString());

        assertThat(result.get("bookmarked")).isEqualTo(false);
        verify(noticeBookmarkRepository).delete(bookmark);
    }

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 시 공고 요약 정보를 반환한다")
    void getMyBookmarks_returnsSummaries() {
        NoticeBookmark bookmark = NoticeBookmark.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notice(notice)
                .createdAt(Instant.parse("2026-03-21T10:00:00Z"))
                .build();
        given(noticeBookmarkRepository.findByUserOrderByCreatedAtDesc(user)).willReturn(List.of(bookmark));

        List<Map<String, Object>> result = bookmarkService.getMyBookmarks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("noticeId")).isEqualTo(notice.getId());
        assertThat(result.get(0).get("statusLabel")).isEqualTo("진행중");
        verify(noticeBookmarkRepository, never()).save(any());
    }
}
