package com.example.demo.service;

import com.example.demo.entity.Report;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.ReportStatus;
import com.example.demo.entity.enums.ReportTargetType;
import com.example.demo.repository.CommunityCommentRepository;
import com.example.demo.repository.CommunityPostRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final Set<ReportStatus> ACTIVE_REPORT_STATUSES = Set.of(ReportStatus.RECEIVED, ReportStatus.REVIEWING);

    private final ReportRepository reportRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> createReport(Map<String, Object> request) {
        User reporter = getCurrentUser();
        ReportTargetType targetType = ReportTargetType.valueOf(String.valueOf(request.getOrDefault("targetType", "COMMUNITY_POST")).trim().toUpperCase());
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

    public List<Map<String, Object>> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateReportStatus(String reportId, Object statusValue) {
        Report report = getReport(reportId);
        ReportStatus status = parseStatus(statusValue);
        report.setStatus(status);
        return toReportResponse(reportRepository.save(report));
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 대상 ID 형식입니다.");
        }
    }

    private Report getReport(String reportId) {
        try {
            return reportRepository.findById(UUID.fromString(reportId))
                    .orElseThrow(() -> new RuntimeException("신고 내역을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 신고 ID 형식입니다.");
        }
    }

    private void validateDuplicateReport(User reporter, ReportTargetType targetType, UUID targetId) {
        boolean duplicated = reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatusIn(
                reporter,
                targetType,
                targetId,
                ACTIVE_REPORT_STATUSES
        );
        if (duplicated) {
            throw new RuntimeException("이미 접수되어 처리 중인 신고입니다.");
        }
    }

    private void validateTargetExists(ReportTargetType targetType, UUID targetId) {
        switch (targetType) {
            case PET_NOTICE -> petNoticeRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("신고 대상 실종 공고를 찾을 수 없습니다."));
            case COMMUNITY_POST -> communityPostRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("신고 대상 커뮤니티 게시글을 찾을 수 없습니다."));
            case COMMUNITY_COMMENT -> communityCommentRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("신고 대상 댓글을 찾을 수 없습니다."));
            case USER -> userRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("신고 대상 사용자를 찾을 수 없습니다."));
        }
    }

    private ReportStatus parseStatus(Object statusValue) {
        if (statusValue == null || String.valueOf(statusValue).isBlank()) {
            throw new RuntimeException("status는 필수입니다.");
        }
        try {
            return ReportStatus.valueOf(String.valueOf(statusValue).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 신고 상태입니다.");
        }
    }

    private Map<String, Object> toReportResponse(Report report) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", report.getId());
        response.put("reporterId", report.getReporter().getId());
        response.put("targetType", report.getTargetType().name());
        response.put("targetId", report.getTargetId());
        response.put("reason", report.getReason());
        response.put("description", report.getDescription());
        response.put("status", report.getStatus().name());
        response.put("createdAt", report.getCreatedAt());
        return response;
    }
}
