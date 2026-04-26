package com.example.pogun.service.report;

import com.example.pogun.dto.admin.AdminReportReviewRequest;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.report.enums.ReportProcessAction;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private CommunityPostRepository communityPostRepository;
    @Mock
    private CommunityCommentRepository communityCommentRepository;
    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AdminService adminService;

    @InjectMocks
    private ReportService reportService;

    private User reporter;
    private User author;
    private PetNotice notice;

    @BeforeEach
    void setUp() {
        reporter = user("reporter-uid", "reporter@test.dev", "신고자", UserStatus.ACTIVE);
        author = user("author-uid", "author@test.dev", "작성자", UserStatus.ACTIVE);
        notice = notice(author);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reporter.getFirebaseUid(), null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReport_acceptsPetNoticeReport() {
        when(userRepository.findByFirebaseUid(reporter.getFirebaseUid())).thenReturn(Optional.of(reporter));
        when(petNoticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(eq(reporter), eq(ReportTargetType.PET_NOTICE), eq(notice.getId()), any()))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> savedReport(invocation.getArgument(0)));

        ReportResponse response = reportService.createReport(request("PET_NOTICE", notice.getId(), "FALSE_INFORMATION"));

        assertThat(response.targetType()).isEqualTo("PET_NOTICE");
        assertThat(response.targetId()).isEqualTo(notice.getId());
        assertThat(response.status()).isEqualTo("RECEIVED");
    }

    @Test
    void createReport_acceptsNoticeChatRoomReportFromParticipant() {
        NoticeChatRoom room = room(notice, author, reporter);
        when(userRepository.findByFirebaseUid(reporter.getFirebaseUid())).thenReturn(Optional.of(reporter));
        when(noticeChatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(eq(reporter), eq(ReportTargetType.NOTICE_CHAT_ROOM), eq(room.getId()), any()))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> savedReport(invocation.getArgument(0)));

        ReportResponse response = reportService.createReport(request("NOTICE_CHAT_ROOM", room.getId(), "ETC"));

        assertThat(response.targetType()).isEqualTo("NOTICE_CHAT_ROOM");
        assertThat(response.targetId()).isEqualTo(room.getId());
    }

    @Test
    void createReport_rejectsNoticeChatRoomReportFromNonParticipant() {
        User other = user("other-uid", "other@test.dev", "제3자", UserStatus.ACTIVE);
        NoticeChatRoom room = room(notice, author, other);
        when(userRepository.findByFirebaseUid(reporter.getFirebaseUid())).thenReturn(Optional.of(reporter));
        when(noticeChatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> reportService.createReport(request("NOTICE_CHAT_ROOM", room.getId(), "ETC")))
                .isInstanceOf(ApiException.class)
                .hasMessage("참여 중인 채팅방만 신고할 수 있습니다.")
                .extracting("code")
                .isEqualTo("REPORT_CHAT_ROOM_FORBIDDEN");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_rejectsDuplicateActiveReport() {
        when(userRepository.findByFirebaseUid(reporter.getFirebaseUid())).thenReturn(Optional.of(reporter));
        when(petNoticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(eq(reporter), eq(ReportTargetType.PET_NOTICE), eq(notice.getId()), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(request("PET_NOTICE", notice.getId(), "ETC")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_REPORT");
    }

    @Test
    void createReport_rejectsMissingTarget() {
        UUID missingNoticeId = UUID.randomUUID();
        when(userRepository.findByFirebaseUid(reporter.getFirebaseUid())).thenReturn(Optional.of(reporter));
        when(petNoticeRepository.findById(missingNoticeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(request("PET_NOTICE", missingNoticeId, "ETC")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("REPORT_TARGET_NOT_FOUND");
    }

    @Test
    void getReports_filtersByStatusAndTargetTypeLatestFirst() {
        Report oldReport = report(ReportTargetType.PET_NOTICE, notice.getId(), ReportStatus.RECEIVED, reporter);
        oldReport.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        Report latestReport = report(ReportTargetType.PET_NOTICE, UUID.randomUUID(), ReportStatus.RECEIVED, reporter);
        latestReport.setCreatedAt(Instant.parse("2026-04-02T00:00:00Z"));
        Report ignoredReport = report(ReportTargetType.USER, UUID.randomUUID(), ReportStatus.REJECTED, reporter);
        ignoredReport.setCreatedAt(Instant.parse("2026-04-03T00:00:00Z"));
        when(reportRepository.findAll()).thenReturn(List.of(oldReport, ignoredReport, latestReport));

        var response = reportService.getReports("RECEIVED", "PET_NOTICE", 0, 10);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.items()).extracting(ReportResponse::id)
                .containsExactly(latestReport.getId(), oldReport.getId());
    }

    @Test
    void reviewReport_hidesTargetAndStoresReviewHistory() {
        User admin = user("admin-uid", "admin@test.dev", "관리자", UserStatus.ACTIVE);
        admin.setRole(UserRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getFirebaseUid(), null, List.of())
        );
        Report report = report(ReportTargetType.PET_NOTICE, notice.getId(), ReportStatus.RECEIVED, reporter);
        when(userRepository.findByFirebaseUid(admin.getFirebaseUid())).thenReturn(Optional.of(admin));
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);
        when(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.PET_NOTICE, notice.getId())).thenReturn(1L);
        when(petNoticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));

        AdminReportReviewRequest request = new AdminReportReviewRequest();
        request.setStatus("RESOLVED");
        request.setProcessAction("HIDE_TARGET");
        request.setProcessReason("허위 공고");

        var response = reportService.reviewReport(report.getId().toString(), request);

        verify(adminService).updateMissingPostVisibility(notice.getId().toString(), "HIDDEN");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getReviewedBy()).isEqualTo(admin);
        assertThat(report.getProcessReason()).isEqualTo("허위 공고");
        assertThat(report.getProcessedAction()).isEqualTo(ReportProcessAction.HIDE_TARGET);
        assertThat(response.processedAction()).isEqualTo("HIDE_TARGET");
    }

    @Test
    void reviewReport_sanctionsTargetAuthor() {
        User admin = user("admin-uid", "admin@test.dev", "관리자", UserStatus.ACTIVE);
        admin.setRole(UserRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getFirebaseUid(), null, List.of())
        );
        CommunityPost post = CommunityPost.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("신고 대상 글")
                .content("내용")
                .status(CommunityPostStatus.ACTIVE)
                .build();
        Report report = report(ReportTargetType.COMMUNITY_POST, post.getId(), ReportStatus.RECEIVED, reporter);
        when(userRepository.findByFirebaseUid(admin.getFirebaseUid())).thenReturn(Optional.of(admin));
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(communityPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(reportRepository.save(report)).thenReturn(report);
        when(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.COMMUNITY_POST, post.getId())).thenReturn(1L);

        AdminReportReviewRequest request = new AdminReportReviewRequest();
        request.setStatus("RESOLVED");
        request.setProcessAction("SANCTION_TARGET_USER");

        reportService.reviewReport(report.getId().toString(), request);

        verify(adminService).sanctionUser(author.getId().toString(), "BANNED");
    }

    private Map<String, Object> request(String targetType, UUID targetId, String reason) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", targetType);
        request.put("targetId", targetId.toString());
        request.put("reason", reason);
        request.put("description", "테스트 신고");
        return request;
    }

    private Report savedReport(Report report) {
        report.setId(UUID.randomUUID());
        report.setCreatedAt(Instant.now());
        return report;
    }

    private Report report(ReportTargetType targetType, UUID targetId, ReportStatus status, User reporter) {
        return Report.builder()
                .id(UUID.randomUUID())
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason("ETC")
                .description("테스트 신고")
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    private NoticeChatRoom room(PetNotice notice, User owner, User guest) {
        return NoticeChatRoom.builder()
                .id(UUID.randomUUID())
                .notice(notice)
                .ownerUser(owner)
                .guestUser(guest)
                .status(NoticeChatRoomStatus.OPEN)
                .build();
    }

    private PetNotice notice(User author) {
        return PetNotice.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("실종 공고")
                .animalType("DOG")
                .gender(PetGender.UNKNOWN)
                .missingDate(Instant.now())
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .hidden(false)
                .build();
    }

    private User user(String firebaseUid, String email, String nickname, UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(status)
                .build();
    }
}
