package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 상태 조회 응답")
public record AdminStatusResponse(
        @Schema(description = "요약") AdminStatusSummaryResponse summary,
        @Schema(description = "관리자 상세 목록") List<AdminStatusItemResponse> admins
) {
}
