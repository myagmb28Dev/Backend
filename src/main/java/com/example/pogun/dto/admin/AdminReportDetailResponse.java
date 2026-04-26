package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "관리자 신고 상세 응답")
public record AdminReportDetailResponse(
        UUID id,
        String targetType,
        UUID targetId,
        String reason,
        String description,
        String status,
        ReporterSummary reporter,
        TargetSummary target,
        long sameTargetReportCount,
        UUID reviewedById,
        String reviewedByNickname,
        String processReason,
        String processedAction,
        Instant reviewedAt,
        Instant createdAt
) {
    public record ReporterSummary(UUID userId, String email, String nickname, String status) {
    }

    public record TargetSummary(UUID targetId, String targetType, String title, UUID authorId, String authorNickname, String status, Boolean hidden) {
    }
}
