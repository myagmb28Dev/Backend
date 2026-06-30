package com.example.pogun.service.bookmark;

import com.example.pogun.dto.bookmark.BookmarkSummaryResponse;
import com.example.pogun.entity.bookmark.NoticeBookmark;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookmarkServiceIntegrationTest extends IntegrationTestProperties {

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetNoticeRepository petNoticeRepository;

    @Autowired
    private NoticeBookmarkRepository noticeBookmarkRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyBookmarks_returnsNoticeSummariesWithOpenInViewDisabled() {
        User user = userRepository.save(User.builder()
                .firebaseUid("bookmark-user-" + UUID.randomUUID())
                .email("bookmark-user-" + UUID.randomUUID() + "@local.test")
                .nickname("bookmark-user")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        PetNotice notice = petNoticeRepository.saveAndFlush(PetNotice.builder()
                .author(user)
                .title("Lost dog")
                .animalType("DOG")
                .breed("Poodle")
                .gender(PetGender.UNKNOWN)
                .missingDate(Instant.parse("2026-06-08T00:00:00Z"))
                .missingRegion("Seoul")
                .status(PetNoticeStatus.OPEN)
                .build());
        noticeBookmarkRepository.saveAndFlush(NoticeBookmark.builder()
                .user(user)
                .notice(notice)
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), "N/A")
        );

        List<BookmarkSummaryResponse> response = bookmarkService.getMyBookmarks();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).noticeId()).isEqualTo(notice.getId());
        assertThat(response.get(0).title()).isEqualTo("Lost dog");
        assertThat(response.get(0).bookmarked()).isTrue();
    }
}
