package com.example.pogun.service.admin;

import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private PetNoticeRepository petNoticeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private NoticeBookmarkRepository noticeBookmarkRepository;

    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;

    @Mock
    private NoticeChatMessageRepository noticeChatMessageRepository;

    @InjectMocks
    private AdminService adminService;

    private CommunityPost communityPost;
    private PetNotice petNotice;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("admin-user")
                .email("user@example.com")
                .nickname("유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        communityPost = CommunityPost.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("커뮤니티 글")
                .content("본문")
                .status(CommunityPostStatus.ACTIVE)
                .build();

        petNotice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("실종 공고")
                .animalType("DOG")
                .gender(PetGender.UNKNOWN)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .hidden(false)
                .build();
    }

    @Test
    @DisplayName("대시보드는 실제 카운트 값을 조합해 반환한다")
    void getDashboard_returnsRepositoryCounts() {
        given(reportRepository.countByCreatedAtBetween(any(), any())).willReturn(5L);
        given(reportRepository.countByStatusIn(any())).willReturn(2L);
        given(communityPostRepository.countByStatus(CommunityPostStatus.HIDDEN)).willReturn(3L);
        given(petNoticeRepository.countByHiddenTrue()).willReturn(4L);
        given(userRepository.countByStatus(UserStatus.BANNED)).willReturn(1L);

        var result = adminService.getDashboard();

        assertThat(result.todayReports()).isEqualTo(5L);
        assertThat(result.pendingReports()).isEqualTo(2L);
        assertThat(result.hiddenCommunityPosts()).isEqualTo(3L);
        assertThat(result.hiddenMissingPosts()).isEqualTo(4L);
        assertThat(result.sanctionedUsers()).isEqualTo(1L);
    }

    @Test
    @DisplayName("커뮤니티 글 공개 상태 변경 시 status가 바뀐다")
    void updateCommunityVisibility_changesStatus() {
        given(communityPostRepository.findById(communityPost.getId())).willReturn(Optional.of(communityPost));
        given(communityPostRepository.save(communityPost)).willReturn(communityPost);

        var result = adminService.updateCommunityVisibility(communityPost.getId().toString(), "HIDDEN");

        assertThat(communityPost.getStatus()).isEqualTo(CommunityPostStatus.HIDDEN);
        assertThat(result.visibility()).isEqualTo("HIDDEN");
        verify(communityPostRepository).save(communityPost);
    }

    @Test
    @DisplayName("실종 공고 공개 상태 변경 시 hidden 플래그가 바뀐다")
    void updateMissingPostVisibility_changesHiddenFlag() {
        given(petNoticeRepository.findById(petNotice.getId())).willReturn(Optional.of(petNotice));
        given(petNoticeRepository.save(petNotice)).willReturn(petNotice);

        var result = adminService.updateMissingPostVisibility(petNotice.getId().toString(), "HIDDEN");

        assertThat(petNotice.getHidden()).isTrue();
        assertThat(result.visibility()).isEqualTo("HIDDEN");
        verify(petNoticeRepository).save(petNotice);
    }

    @Test
    @DisplayName("사용자 제재 시 상태가 BANNED로 변경된다")
    void sanctionUser_changesStatus() {
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        var result = adminService.sanctionUser(user.getId().toString(), "BAN");

        assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(result.status()).isEqualTo("BANNED");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("커뮤니티 글 강제 삭제는 DELETED 상태로 변경한다")
    void deleteCommunityPost_marksDeleted() {
        given(communityPostRepository.findById(communityPost.getId())).willReturn(Optional.of(communityPost));
        given(communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(communityPost, CommunityCommentStatus.NORMAL)).willReturn(java.util.List.of());
        given(communityPostRepository.save(communityPost)).willReturn(communityPost);

        var result = adminService.deleteCommunityPost(communityPost.getId().toString());

        assertThat(communityPost.getStatus()).isEqualTo(CommunityPostStatus.DELETED);
        assertThat(result.deleted()).isTrue();
        verify(communityPostRepository).save(communityPost);
    }
}



