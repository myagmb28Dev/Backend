package com.example.pogun.service.report;

import com.example.pogun.dto.admin.AdminReportDetailResponse;
import com.example.pogun.dto.admin.AdminReportListResponse;
import com.example.pogun.dto.admin.AdminReportActionRequest;
import com.example.pogun.dto.admin.AdminReportReviewRequest;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.report.enums.ReportProcessAction;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.adminauth.AdminAuditService;
import com.example.pogun.service.adminauth.AdminPrincipal;
import com.example.pogun.service.adminauth.AdminSecurityService;
import com.example.pogun.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 ReportService이다.
 */

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final Set<ReportStatus> ACTIVE_REPORT_STATUSES = Set.of(ReportStatus.RECEIVED, ReportStatus.REVIEWING);

    private final ReportRepository reportRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminService adminService;
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;

    // 신고는 단일 테이블로 관리하되, 저장 전에 대상 존재 여부와 활성 중복 신고를 함께 차단한다.
    @Transactional
    public ReportResponse createReport(Map<String, Object> request) {
        User reporter = getCurrentUser();
        ReportTargetType targetType = parseTargetType(request.getOrDefault("targetType", "COMMUNITY_POST"));
        UUID targetId = parseUuid(String.valueOf(request.get("targetId")));
        String reason = String.valueOf(request.getOrDefault("reason", "ETC")).trim();
        String description = request.get("description") == null ? null : String.valueOf(request.get("description")).trim();

        validateTargetExists(reporter, targetType, targetId);
        validateDuplicateReport(reporter, targetType, targetId);

        Report report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .description(description)
                .status(ReportStatus.RECEIVED)
                .build());
        applyAutoHideThreshold(report);

        return toReportResponse(report);
    }

    public List<ReportResponse> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toReportResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAdminReports(String statusValue, String targetTypeValue, Integer page, Integer pageSize) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        ReportStatus status = parseOptionalAdminStatus(statusValue);
        ReportTargetType targetType = parseOptionalAdminTargetType(targetTypeValue);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedPageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Report> resultPage;
        if (status != null && targetType != null) {
            resultPage = reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(status, targetType, pageable);
        } else if (status != null) {
            resultPage = reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (targetType != null) {
            resultPage = reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType, pageable);
        } else {
            resultPage = reportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<Map<String, Object>> items = resultPage.getContent().stream()
                .map(this::toAdminReportSummaryMap)
                .toList();

        return orderedMap(
                "items", items,
                "page", resolvedPage + 1,
                "pageSize", resolvedPageSize,
                "total", resultPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminReportDetail(String reportId) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        Report report = getReport(reportId);
        return toAdminReportDetailMap(report);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAdminReporters(String reportId) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        Report report = getReport(reportId);
        return reportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(report.getTargetType(), report.getTargetId()).stream()
                .map(candidate -> orderedMap(
                        "userId", candidate.getReporter().getId(),
                        "name", candidate.getReporter().getNickname(),
                        "email", candidate.getReporter().getEmail(),
                        "comment", candidate.getDescription(),
                        "reason", candidate.getReason(),
                        "autoFlagged", false,
                        "reportedAt", candidate.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> dismissReport(String reportId, AdminReportActionRequest request) {
        return applyAdminAction(reportId, ReportStatus.REJECTED, ReportProcessAction.NONE, request);
    }

    @Transactional
    public Map<String, Object> warnReport(String reportId, AdminReportActionRequest request) {
        return applyAdminAction(reportId, ReportStatus.RESOLVED, ReportProcessAction.SANCTION_TARGET_USER, request);
    }

    @Transactional
    public Map<String, Object> deleteTarget(String reportId, AdminReportActionRequest request) {
        return applyAdminAction(reportId, ReportStatus.RESOLVED, ReportProcessAction.DELETE_TARGET, request);
    }

    @Transactional
    public Map<String, Object> resolveReport(String reportId, AdminReportActionRequest request) {
        return applyAdminAction(reportId, ReportStatus.RESOLVED, ReportProcessAction.NONE, request);
    }

    @Transactional(readOnly = true)
    public AdminReportListResponse getReports(String statusValue, String targetTypeValue, int page, int size) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        ReportStatus status = parseOptionalStatus(statusValue);
        ReportTargetType targetType = parseOptionalTargetType(targetTypeValue);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Report> filteredPage;
        if (status != null && targetType != null) {
            filteredPage = reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(status, targetType, pageable);
        } else if (status != null) {
            filteredPage = reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (targetType != null) {
            filteredPage = reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType, pageable);
        } else {
            filteredPage = reportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<ReportResponse> items = filteredPage.getContent().stream()
                .map(this::toReportResponse)
                .toList();

        return new AdminReportListResponse(
                items,
                (int) filteredPage.getTotalElements(),
                filteredPage.getTotalPages(),
                safePage,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public AdminReportDetailResponse getReportDetail(String reportId) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        Report report = getReport(reportId);
        return toAdminReportDetailResponse(report);
    }

    @Transactional
    public ReportResponse updateReportStatus(String reportId, Object statusValue) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        Report report = getReport(reportId);
        ReportStatus beforeStatus = report.getStatus();
        ReportStatus status = parseStatus(statusValue);
        report.setStatus(status);
        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            report.setReviewedAt(Instant.now());
        } else {
            report.setReviewedAt(null);
        }
        Report saved = reportRepository.save(report);
        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            notifyReportResult(saved);
        }
        adminAuditService.log(
                "ADMIN_REPORT_STATUS_UPDATED",
                "REPORT",
                saved.getId().toString(),
                Map.of("status", normalizeAdminReportStatus(beforeStatus)),
                Map.of("status", normalizeAdminReportStatus(saved.getStatus())),
                null
        );
        return toReportResponse(saved);
    }

    @Transactional
    public AdminReportDetailResponse reviewReport(String reportId, AdminReportReviewRequest request) {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        Report report = getReport(reportId);
        ReportStatus beforeStatus = report.getStatus();
        ReportStatus status = parseStatus(request.getStatus());
        ReportProcessAction action = parseProcessAction(request.getProcessAction());
        User reviewer = getCurrentUser();
        String processReason = normalizeNullable(request.getProcessReason());

        applyProcessAction(report, action);
        report.setStatus(status);
        report.setProcessedAction(action);
        report.setProcessReason(processReason);
        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            report.setReviewedAt(Instant.now());
            report.setReviewedBy(reviewer);
        } else {
            report.setReviewedAt(null);
            report.setReviewedBy(null);
        }

        Report saved = reportRepository.save(report);
        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            notifyReportResult(saved);
        }
        adminAuditService.log(
                "ADMIN_REPORT_REVIEWED",
                "REPORT",
                saved.getId().toString(),
                Map.of("status", normalizeAdminReportStatus(beforeStatus)),
                Map.of(
                        "status", normalizeAdminReportStatus(saved.getStatus()),
                        "processAction", saved.getProcessedAction() == null ? "NONE" : saved.getProcessedAction().name()
                ),
                Map.of("processReason", saved.getProcessReason())
        );
        return toAdminReportDetailResponse(saved);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof String firebaseUid) {
            return userRepository.findByFirebaseUid(firebaseUid)
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        }
        if (principal instanceof AdminPrincipal adminPrincipal) {
            return userRepository.findById(adminPrincipal.userId())
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        }
        throw ApiException.unauthorized("USER_REQUIRED", "인증된 사용자가 필요합니다.");
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_TARGET_ID", "올바르지 않은 대상 ID 형식입니다.");
        }
    }

    private Report getReport(String reportId) {
        try {
            return reportRepository.findById(UUID.fromString(reportId)).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "신고 내역을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_REPORT_ID", "올바르지 않은 신고 ID 형식입니다.");
        }
    }

    private void validateDuplicateReport(User reporter, ReportTargetType targetType, UUID targetId) {
        boolean duplicated = reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(reporter, targetType, targetId, ACTIVE_REPORT_STATUSES);
        if (duplicated) {
            throw ApiException.conflict("DUPLICATE_REPORT", "이미 접수되어 처리 중인 신고입니다.");
        }
    }

    // 컨트롤러 종류와 무관하게 같은 검증을 재사용하려고 대상별 존재 확인을 서비스에서 묶어 관리한다.
    private void validateTargetExists(User reporter, ReportTargetType targetType, UUID targetId) {
        switch (targetType) {
            case PET_NOTICE -> petNoticeRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 실종 공고를 찾을 수 없습니다."));
            case COMMUNITY_POST -> communityPostRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 커뮤니티 게시글을 찾을 수 없습니다."));
            case COMMUNITY_COMMENT -> communityCommentRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 댓글을 찾을 수 없습니다."));
            case NOTICE_CHAT_ROOM -> {
                var room = noticeChatRoomRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 채팅방을 찾을 수 없습니다."));
                boolean participant = room.getOwnerUser().getId().equals(reporter.getId())
                        || room.getGuestUser().getId().equals(reporter.getId());
                if (!participant) {
                    throw ApiException.forbidden("REPORT_CHAT_ROOM_FORBIDDEN", "참여 중인 채팅방만 신고할 수 있습니다.");
                }
            }
            case USER -> userRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 사용자를 찾을 수 없습니다."));
        }
    }

    private ReportTargetType parseTargetType(Object targetTypeValue) {
        if (targetTypeValue == null || String.valueOf(targetTypeValue).isBlank()) {
            throw ApiException.badRequest("MISSING_TARGET_TYPE", "targetType은 필수입니다.");
        }
        try {
            return ReportTargetType.valueOf(String.valueOf(targetTypeValue).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_TARGET_TYPE", "올바르지 않은 targetType 값입니다.");
        }
    }

    private ReportTargetType parseOptionalTargetType(String targetTypeValue) {
        if (targetTypeValue == null || targetTypeValue.isBlank()) {
            return null;
        }
        return parseTargetType(targetTypeValue);
    }

    private ReportTargetType parseOptionalAdminTargetType(String targetTypeValue) {
        if (targetTypeValue == null || targetTypeValue.isBlank()) {
            return null;
        }
        return parseAdminTargetType(targetTypeValue);
    }

    private ReportStatus parseStatus(Object statusValue) {
        if (statusValue == null || String.valueOf(statusValue).isBlank()) {
            throw ApiException.badRequest("MISSING_REPORT_STATUS", "status는 필수입니다.");
        }
        try {
            return ReportStatus.valueOf(String.valueOf(statusValue).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_REPORT_STATUS", "올바르지 않은 신고 상태입니다.");
        }
    }

    private ReportStatus parseOptionalStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            return null;
        }
        return parseStatus(statusValue);
    }

    private ReportStatus parseOptionalAdminStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            return null;
        }
        return parseAdminStatus(statusValue);
    }

    private ReportStatus parseAdminStatus(String statusValue) {
        String normalized = statusValue.trim().toUpperCase();
        return switch (normalized) {
            case "PENDING", "RECEIVED" -> ReportStatus.RECEIVED;
            case "REVIEWING" -> ReportStatus.REVIEWING;
            case "RESOLVED" -> ReportStatus.RESOLVED;
            case "REJECTED", "DISMISSED" -> ReportStatus.REJECTED;
            default -> throw ApiException.badRequest("INVALID_REPORT_STATUS", "올바르지 않은 신고 상태입니다.");
        };
    }

    private ReportTargetType parseAdminTargetType(String targetTypeValue) {
        String normalized = targetTypeValue.trim().toUpperCase();
        return switch (normalized) {
            case "NOTICE", "PET_NOTICE" -> ReportTargetType.PET_NOTICE;
            case "COMMUNITY_POST" -> ReportTargetType.COMMUNITY_POST;
            case "COMMUNITY_COMMENT" -> ReportTargetType.COMMUNITY_COMMENT;
            case "NOTICE_CHAT_ROOM" -> ReportTargetType.NOTICE_CHAT_ROOM;
            case "USER" -> ReportTargetType.USER;
            default -> throw ApiException.badRequest("INVALID_TARGET_TYPE", "올바르지 않은 targetType 값입니다.");
        };
    }

    private ReportProcessAction parseProcessAction(String actionValue) {
        if (actionValue == null || actionValue.isBlank()) {
            return ReportProcessAction.NONE;
        }
        try {
            return ReportProcessAction.valueOf(actionValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_REPORT_PROCESS_ACTION", "올바르지 않은 처리 액션입니다.");
        }
    }

    private void applyProcessAction(Report report, ReportProcessAction action) {
        switch (action) {
            case NONE -> {
            }
            case HIDE_TARGET -> hideReportTarget(report);
            case DELETE_TARGET -> deleteReportTarget(report);
            case SANCTION_TARGET_USER -> sanctionReportTargetUser(report);
        }
    }

    private Map<String, Object> applyAdminAction(String reportId, ReportStatus status, ReportProcessAction action, AdminReportActionRequest request) {
        Report report = getReport(reportId);
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        ReportStatus beforeStatus = report.getStatus();
        String memo = request == null ? null : normalizeNullable(request.getMemo());

        applyProcessAction(report, action);
        report.setStatus(status);
        report.setProcessedAction(action == ReportProcessAction.NONE ? null : action);
        report.setProcessReason(memo);
        report.setReviewedAt(Instant.now());
        report.setReviewedBy(getCurrentUser());

        Report saved = reportRepository.save(report);
        notifyReportResult(saved);
        Map<String, Object> response = toAdminReportDetailMap(saved);
        adminAuditService.log(
                "ADMIN_REPORT_ACTION_APPLIED",
                "REPORT",
                saved.getId().toString(),
                Map.of("status", normalizeAdminReportStatus(beforeStatus)),
                Map.of("status", normalizeAdminReportStatus(saved.getStatus()), "processAction", action.name()),
                memo == null ? null : Map.of("memo", memo)
        );
        return response;
    }

    private void hideReportTarget(Report report) {
        switch (report.getTargetType()) {
            case PET_NOTICE -> adminService.updateMissingPostVisibility(report.getTargetId().toString(), "HIDDEN");
            case COMMUNITY_POST -> adminService.updateCommunityVisibility(report.getTargetId().toString(), "HIDDEN");
            default -> throw ApiException.badRequest("UNSUPPORTED_REPORT_ACTION", "해당 신고 대상은 숨김 처리할 수 없습니다.");
        }
    }

    private void deleteReportTarget(Report report) {
        switch (report.getTargetType()) {
            case PET_NOTICE -> adminService.deleteMissingPost(report.getTargetId().toString());
            case COMMUNITY_POST -> adminService.deleteCommunityPost(report.getTargetId().toString());
            default -> throw ApiException.badRequest("UNSUPPORTED_REPORT_ACTION", "해당 신고 대상은 삭제 처리할 수 없습니다.");
        }
    }

    private void sanctionReportTargetUser(Report report) {
        User targetUser = resolveTargetUser(report);
        if (targetUser == null) {
            throw ApiException.badRequest("UNSUPPORTED_REPORT_ACTION", "해당 신고 대상에서 제재할 사용자를 찾을 수 없습니다.");
        }
        adminService.sanctionUser(targetUser.getId().toString(), UserStatus.BANNED.name());
    }

    private User resolveTargetUser(Report report) {
        return switch (report.getTargetType()) {
            case USER -> userRepository.findById(report.getTargetId()).orElse(null);
            case PET_NOTICE -> petNoticeRepository.findById(report.getTargetId()).map(PetNotice::getAuthor).orElse(null);
            case COMMUNITY_POST -> communityPostRepository.findById(report.getTargetId()).map(CommunityPost::getAuthor).orElse(null);
            case COMMUNITY_COMMENT -> communityCommentRepository.findById(report.getTargetId()).map(CommunityComment::getAuthor).orElse(null);
            case NOTICE_CHAT_ROOM -> null;
        };
    }

    private ReportResponse toReportResponse(Report report) {
        return new ReportResponse(report.getId(), report.getReporter().getId(), report.getTargetType().name(), report.getTargetId(), report.getReason(), report.getDescription(), report.getStatus().name(), report.getReviewedAt(), report.getCreatedAt());
    }

    private AdminReportDetailResponse toAdminReportDetailResponse(Report report) {
        User reporter = report.getReporter();
        User reviewedBy = report.getReviewedBy();
        return new AdminReportDetailResponse(
                report.getId(),
                report.getTargetType().name(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus().name(),
                new AdminReportDetailResponse.ReporterSummary(
                        reporter.getId(),
                        reporter.getEmail(),
                        reporter.getNickname(),
                        reporter.getStatus() == null ? null : reporter.getStatus().name()
                ),
                resolveTargetSummary(report),
                reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getNickname(),
                report.getProcessReason(),
                report.getProcessedAction() == null ? null : report.getProcessedAction().name(),
                report.getReviewedAt(),
                report.getCreatedAt()
        );
    }

    private AdminReportDetailResponse.TargetSummary resolveTargetSummary(Report report) {
        UUID targetId = report.getTargetId();
        return switch (report.getTargetType()) {
            case PET_NOTICE -> petNoticeRepository.findById(targetId)
                    .map(notice -> new AdminReportDetailResponse.TargetSummary(
                            targetId,
                            report.getTargetType().name(),
                            notice.getTitle(),
                            notice.getAuthor().getId(),
                            notice.getAuthor().getNickname(),
                            notice.getStatus().name(),
                            notice.getHidden()
                    ))
                    .orElse(missingTargetSummary(report));
            case COMMUNITY_POST -> communityPostRepository.findById(targetId)
                    .map(post -> new AdminReportDetailResponse.TargetSummary(
                            targetId,
                            report.getTargetType().name(),
                            post.getTitle(),
                            post.getAuthor().getId(),
                            post.getAuthor().getNickname(),
                            post.getStatus().name(),
                            null
                    ))
                    .orElse(missingTargetSummary(report));
            case COMMUNITY_COMMENT -> communityCommentRepository.findById(targetId)
                    .map(comment -> new AdminReportDetailResponse.TargetSummary(
                            targetId,
                            report.getTargetType().name(),
                            abbreviate(comment.getContent()),
                            comment.getAuthor().getId(),
                            comment.getAuthor().getNickname(),
                            comment.getStatus().name(),
                            null
                    ))
                    .orElse(missingTargetSummary(report));
            case USER -> userRepository.findById(targetId)
                    .map(user -> new AdminReportDetailResponse.TargetSummary(
                            targetId,
                            report.getTargetType().name(),
                            user.getNickname(),
                            user.getId(),
                            user.getNickname(),
                            user.getStatus() == null ? null : user.getStatus().name(),
                            null
                    ))
                    .orElse(missingTargetSummary(report));
            case NOTICE_CHAT_ROOM -> noticeChatRoomRepository.findById(targetId)
                    .map(room -> new AdminReportDetailResponse.TargetSummary(
                            targetId,
                            report.getTargetType().name(),
                            room.getNotice().getTitle(),
                            room.getNotice().getAuthor().getId(),
                            room.getNotice().getAuthor().getNickname(),
                            room.getStatus().name(),
                            null
                    ))
                    .orElse(missingTargetSummary(report));
        };
    }

    private Map<String, Object> toAdminReportSummaryMap(Report report) {
        return orderedMap(
                "id", report.getId(),
                "targetType", normalizeAdminTargetType(report.getTargetType()),
                "reason", report.getReason(),
                "reporterCount", reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()),
                "status", normalizeAdminReportStatus(report.getStatus()),
                "lastReportedAt", report.getCreatedAt(),
                "targetId", report.getTargetId()
        );
    }

    private Map<String, Object> toAdminReportDetailMap(Report report) {
        AdminReportDetailResponse.TargetSummary target = resolveTargetSummary(report);
        return orderedMap(
                "id", report.getId(),
                "targetType", normalizeAdminTargetType(report.getTargetType()),
                "targetId", report.getTargetId(),
                "reason", report.getReason(),
                "description", report.getDescription(),
                "status", normalizeAdminReportStatus(report.getStatus()),
                "reporterCount", reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()),
                "reporters", getAdminReporters(report.getId().toString()),
                "targetTitle", target.title(),
                "targetUrl", resolveTargetUrl(report),
                "target", orderedMap(
                        "targetId", target.targetId(),
                        "targetType", target.targetType(),
                        "title", target.title(),
                        "authorId", target.authorId(),
                        "authorNickname", target.authorNickname(),
                        "status", target.status(),
                        "hidden", target.hidden()
                ),
                "reviewedById", report.getReviewedBy() == null ? null : report.getReviewedBy().getId(),
                "reviewedByNickname", report.getReviewedBy() == null ? null : report.getReviewedBy().getNickname(),
                "processReason", report.getProcessReason(),
                "processedAction", report.getProcessedAction() == null ? null : report.getProcessedAction().name(),
                "reviewedAt", report.getReviewedAt(),
                "createdAt", report.getCreatedAt()
        );
    }

    private AdminReportDetailResponse.TargetSummary missingTargetSummary(Report report) {
        return new AdminReportDetailResponse.TargetSummary(report.getTargetId(), report.getTargetType().name(), "삭제된 대상", null, null, "DELETED_OR_MISSING", null);
    }

    private String resolveTargetUrl(Report report) {
        return switch (report.getTargetType()) {
            case PET_NOTICE -> "/api/admin/notices/" + report.getTargetId();
            case COMMUNITY_POST -> "/api/admin/community/posts/" + report.getTargetId();
            case COMMUNITY_COMMENT -> "/api/admin/community/posts/" + report.getTargetId();
            case NOTICE_CHAT_ROOM -> "/api/admin/reports/" + report.getId();
            case USER -> "/api/admin/users/" + report.getTargetId();
        };
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private void notifyReportResult(Report report) {
        String statusLabel = report.getStatus() == ReportStatus.RESOLVED ? "처리 완료" : "반려";
        notificationService.createAndSendNotification(
                report.getReporter(),
                null,
                NotificationType.REPORT_RESULT,
                NotificationTargetType.REPORT,
                report.getId(),
                "신고 처리 결과가 도착했습니다.",
                "신고가 " + statusLabel + "되었습니다.",
                NotificationPriority.HIGH,
                "report-result:reporter:" + report.getReporter().getId() + ":" + report.getId() + ":" + report.getStatus(),
                Map.of("reportId", report.getId().toString(), "status", report.getStatus().name())
        );

        User targetAuthor = resolveCommunityTargetAuthor(report);
        if (targetAuthor != null) {
            notificationService.createAndSendNotification(
                    targetAuthor,
                    null,
                    NotificationType.REPORT_RESULT,
                    NotificationTargetType.REPORT,
                    report.getId(),
                    "내 커뮤니티 콘텐츠 신고 결과",
                    "내 커뮤니티 콘텐츠에 대한 신고가 " + statusLabel + "되었습니다.",
                    NotificationPriority.HIGH,
                    "report-result:target:" + targetAuthor.getId() + ":" + report.getId() + ":" + report.getStatus(),
                    Map.of("reportId", report.getId().toString(), "status", report.getStatus().name())
            );
        }
    }

    private User resolveCommunityTargetAuthor(Report report) {
        if (report.getTargetType() == ReportTargetType.COMMUNITY_POST) {
            return communityPostRepository.findById(report.getTargetId())
                    .map(CommunityPost::getAuthor)
                    .orElse(null);
        }
        if (report.getTargetType() == ReportTargetType.COMMUNITY_COMMENT) {
            return communityCommentRepository.findById(report.getTargetId())
                    .map(CommunityComment::getAuthor)
                    .orElse(null);
        }
        return null;
    }

    private void applyAutoHideThreshold(Report report) {
        if (report.getTargetType() != ReportTargetType.PET_NOTICE) {
            return;
        }
        long reportCount = reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId());
        if (reportCount < 5L) {
            return;
        }
        petNoticeRepository.findById(report.getTargetId()).ifPresent(notice -> {
            if (Boolean.TRUE.equals(notice.getHidden())) {
                return;
            }
            notice.setHidden(true);
            petNoticeRepository.save(notice);
            adminAuditService.log(
                    "AUTO_NOTICE_HIDDEN_BY_REPORT_THRESHOLD",
                    "PET_NOTICE",
                    notice.getId().toString(),
                    Map.of("hidden", false, "reportCount", reportCount),
                    Map.of("hidden", true, "reportCount", reportCount),
                    null
            );
        });
    }

    private String normalizeAdminTargetType(ReportTargetType targetType) {
        return switch (targetType) {
            case PET_NOTICE -> "NOTICE";
            case COMMUNITY_POST -> "COMMUNITY_POST";
            case COMMUNITY_COMMENT -> "COMMUNITY_COMMENT";
            case NOTICE_CHAT_ROOM -> "NOTICE_CHAT_ROOM";
            case USER -> "USER";
        };
    }

    private String normalizeAdminReportStatus(ReportStatus status) {
        return status == ReportStatus.RECEIVED ? "PENDING" : status.name();
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }
}
