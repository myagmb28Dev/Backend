package com.example.pogun.service.report;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.dto.community.CommunityCommentReportRequest;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.adminauth.AdminAuditService;
import com.example.pogun.service.adminauth.AdminSecurityService;
import com.example.pogun.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private AdminSecurityService adminSecurityService;
    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private ReportService reportService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReport_hidesNoticeWhenActiveReportCountReachesFive() {
        UUID noticeId = UUID.randomUUID();
        User reporter = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("reporter-uid")
                .email("reporter@example.com")
                .nickname("신고자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        PetNotice notice = PetNotice.builder()
                .id(noticeId)
                .author(reporter)
                .title("테스트 공고")
                .hidden(false)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reporter-uid", "token")
        );

        when(userRepository.findByFirebaseUid("reporter-uid")).thenReturn(Optional.of(reporter));
        when(petNoticeRepository.findById(noticeId)).thenReturn(Optional.of(notice));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.PET_NOTICE, noticeId)).thenReturn(5L);
        when(petNoticeRepository.save(any(PetNotice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reportService.createReport(Map.of(
                "targetType", "PET_NOTICE",
                "targetId", noticeId.toString(),
                "reason", "SPAM"
        ));

        ArgumentCaptor<PetNotice> noticeCaptor = ArgumentCaptor.forClass(PetNotice.class);
        verify(petNoticeRepository).save(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getHidden()).isTrue();
    }

    @Test
    void listAdminReports_mapsReceivedStatusToPending() {
        UUID reportId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User reporter = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("admin-uid")
                .email("admin@example.com")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        Report report = Report.builder()
                .id(reportId)
                .reporter(reporter)
                .targetType(ReportTargetType.PET_NOTICE)
                .targetId(targetId)
                .reason("ABUSE")
                .status(ReportStatus.RECEIVED)
                .createdAt(Instant.parse("2026-04-26T00:00:00Z"))
                .build();

        when(reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.PET_NOTICE, targetId)).thenReturn(1L);

        Map<String, Object> result = reportService.listAdminReports("PENDING", "NOTICE", 1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("status")).isEqualTo("PENDING");
        assertThat(items.get(0).get("targetType")).isEqualTo("NOTICE");
    }

    @Test
    void createCommunityCommentReport_createsReportForActiveComment() {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        User reporter = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("reporter-uid")
                .email("reporter@example.com")
                .nickname("댓글신고자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        CommunityPost post = CommunityPost.builder()
                .id(postId)
                .author(reporter)
                .title("커뮤니티 글")
                .build();
        CommunityComment comment = CommunityComment.builder()
                .id(commentId)
                .post(post)
                .author(reporter)
                .content("문제 댓글")
                .status(CommunityCommentStatus.NORMAL)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reporter-uid", "token")
        );

        when(userRepository.findByFirebaseUid("reporter-uid")).thenReturn(Optional.of(reporter));
        when(communityCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunityCommentReportRequest request = new CommunityCommentReportRequest();
        request.setReason("ABUSE");
        request.setDescription("욕설이 있습니다.");

        reportService.createCommunityCommentReport(postId.toString(), commentId.toString(), request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getTargetType()).isEqualTo(ReportTargetType.COMMUNITY_COMMENT);
        assertThat(reportCaptor.getValue().getTargetId()).isEqualTo(commentId);
        assertThat(reportCaptor.getValue().getReason()).isEqualTo("ABUSE");
        assertThat(reportCaptor.getValue().getDescription()).isEqualTo("욕설이 있습니다.");
    }
}
