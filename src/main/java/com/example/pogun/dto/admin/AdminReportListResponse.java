package com.example.pogun.dto.admin;

import com.example.pogun.dto.report.ReportResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 신고 목록 응답")
public record AdminReportListResponse(
        List<ReportResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
