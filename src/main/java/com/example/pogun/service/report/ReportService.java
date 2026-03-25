package com.example.pogun.service.report;

import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.report.ReportStatus;
import com.example.pogun.entity.report.ReportTargetType;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final UserRepository userRepository;

    // 신고는 단일 테이블로 관리하되, 저장 전에 대상 존재 여부와 활성 중복 신고를 함께 차단한다.
    @Transactional
    public ReportResponse createReport(Map<String, Object> request) {
        User reporter = getCurrentUser();
        ReportTargetType targetType = parseTargetType(request.getOrDefault("targetType", "COMMUNITY_POST"));
        UUID targetId = parseUuid(String.valueOf(request.get("targetId")));
        String reason = String.valueOf(request.getOrDefault("reason", "ETC")).trim();
        String description = request.get("description") == null ? null : String.valueOf(request.get("description")).trim();

        validateTargetExists(targetType, targetId);
        validateDuplicateReport(reporter, targetType, targetId);

        Report report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .description(description)
                .status(ReportStatus.RECEIVED)
                .build());

        return toReportResponse(report);
    }

    public List<ReportResponse> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toReportResponse).toList();
    }

    @Transactional
    public ReportResponse updateReportStatus(String reportId, Object statusValue) {
        Report report = getReport(reportId);
        ReportStatus status = parseStatus(statusValue);
        report.setStatus(status);
        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            report.setReviewedAt(Instant.now());
        } else {
            report.setReviewedAt(null);
        }
        return toReportResponse(reportRepository.save(report));
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
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
    private void validateTargetExists(ReportTargetType targetType, UUID targetId) {
        switch (targetType) {
            case PET_NOTICE -> petNoticeRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 실종 공고를 찾을 수 없습니다."));
            case COMMUNITY_POST -> communityPostRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 커뮤니티 게시글을 찾을 수 없습니다."));
            case COMMUNITY_COMMENT -> communityCommentRepository.findById(targetId).orElseThrow(() -> ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고 대상 댓글을 찾을 수 없습니다."));
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

    private ReportResponse toReportResponse(Report report) {
        return new ReportResponse(report.getId(), report.getReporter().getId(), report.getTargetType().name(), report.getTargetId(), report.getReason(), report.getDescription(), report.getStatus().name(), report.getReviewedAt(), report.getCreatedAt());
    }
}