package com.example.demo.service;

import com.example.demo.entity.NoticeBookmark;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.NotificationTargetType;
import com.example.demo.entity.enums.NotificationType;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissingPetServiceTest {

    @Mock
    private PetNoticeRepository petNoticeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NoticeBookmarkRepository noticeBookmarkRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MissingPetService missingPetService;

    private User author;

    @BeforeEach
    void setUp() {
        author = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("user@example.com")
                .nickname("작성자")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid-1", null)
        );

        lenient().when(userRepository.findByFirebaseUid("firebase-uid-1")).thenReturn(Optional.of(author));
        lenient().when(petNoticeRepository.save(any(PetNotice.class))).thenAnswer(invocation -> {
            PetNotice notice = invocation.getArgument(0);
            if (notice.getId() == null) {
                notice.setId(UUID.randomUUID());
            }
            return notice;
        });
        lenient().when(noticeBookmarkRepository.countByNotice(any(PetNotice.class))).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("실종 공고 생성 시 공고를 저장하고 알림을 보낸다")
    void createMissingPet_savesNoticeAndSendsNotification() {
        Map<String, Object> request = Map.of(
                "title", "말티즈를 찾습니다",
                "animalType", "DOG",
                "breed", "말티즈",
                "gender", "MALE",
                "missingDate", "2026-03-20T10:00:00Z",
                "missingRegion", "서울",
                "status", "OPEN",
                "imageUrls", List.of("https://example.com/a.jpg")
        );

        Map<String, Object> result = missingPetService.createMissingPet(request);

        ArgumentCaptor<PetNotice> noticeCaptor = ArgumentCaptor.forClass(PetNotice.class);
        verify(petNoticeRepository).save(noticeCaptor.capture());
        PetNotice saved = noticeCaptor.getValue();

        assertThat(saved.getTitle()).isEqualTo("말티즈를 찾습니다");
        assertThat(saved.getAnimalType()).isEqualTo("DOG");
        assertThat(saved.getBreed()).isEqualTo("말티즈");
        assertThat(saved.getGender()).isEqualTo(PetGender.MALE);
        assertThat(saved.getImages()).hasSize(1);
        assertThat(result.get("title")).isEqualTo("말티즈를 찾습니다");

        verify(notificationService).createAndSendNotification(
                eq(author),
                eq(NotificationType.NEW_NOTICE),
                eq(NotificationTargetType.PET_NOTICE),
                any(UUID.class),
                eq("실종 공고가 등록되었습니다."),
                eq("말티즈를 찾습니다 공고 등록이 완료되었습니다."),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("실종 공고 목록 조회 시 필터링 결과와 페이지 정보를 반환한다")
    void getMissingPetList_returnsPagedItems() {
        PetNotice notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("공고 제목")
                .animalType("DOG")
                .breed("말티즈")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(3L)
                .build();
        given(petNoticeRepository.findNotices("서울", "말티즈", PetNoticeStatus.OPEN, null, null))
                .willReturn(List.of(notice));

        Map<String, Object> result = missingPetService.getMissingPetList("서울", "말티즈", "OPEN", null, null, "latest", 0, 20);

        assertThat(result.get("totalElements")).isEqualTo(1);
        List<?> items = (List<?>) result.get("items");
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("실종 공고 상태 변경 시 저장 후 북마크 사용자에게 알림을 보낸다")
    void changeMissingPetStatus_updatesStatusAndSendsBookmarkNotifications() {
        User bookmarkUser = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-2")
                .email("bookmark@example.com")
                .nickname("북마크유저")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        PetNotice notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("공고 제목")
                .animalType("DOG")
                .breed("말티즈")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(3L)
                .build();
        NoticeBookmark bookmark = NoticeBookmark.builder()
                .id(UUID.randomUUID())
                .user(bookmarkUser)
                .notice(notice)
                .build();

        given(petNoticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        given(noticeBookmarkRepository.findByNotice(notice)).willReturn(List.of(bookmark));

        Map<String, Object> result = missingPetService.changeMissingPetStatus(notice.getId().toString(), "RESOLVED");

        assertThat(result.get("status")).isEqualTo("RESOLVED");
        assertThat(notice.getStatus()).isEqualTo(PetNoticeStatus.RESOLVED);
        verify(notificationService).createAndSendNotification(
                eq(bookmarkUser),
                eq(NotificationType.NOTICE_STATUS_CHANGED),
                eq(NotificationTargetType.PET_NOTICE),
                eq(notice.getId()),
                eq("즐겨찾기한 공고 상태가 변경되었습니다."),
                eq("공고 제목 공고 상태가 해결됨로 변경되었습니다."),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("조회수 증가 시 viewCount를 1 올린다")
    void increaseMissingPetView_incrementsCount() {
        PetNotice notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("공고 제목")
                .animalType("DOG")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(5L)
                .build();
        given(petNoticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));

        Map<String, Object> result = missingPetService.increaseMissingPetView(notice.getId().toString());

        assertThat(result.get("viewCount")).isEqualTo(6L);
        assertThat(notice.getViewCount()).isEqualTo(6L);
        verify(petNoticeRepository).save(notice);
    }
}

