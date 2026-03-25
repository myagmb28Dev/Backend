package com.example.pogun.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 ReportResponse이다.
 */

@Schema(description = "신고 응답")
public record ReportResponse(
        UUID id,
        UUID reporterId,
        String targetType,
        UUID targetId,
        String reason,
        String description,
        String status,
        Instant reviewedAt,
        Instant createdAt
) {
}