package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 상태 요약")
public record AdminStatusSummaryResponse(
        @Schema(description = "전체 관리자 수") long totalAdmins,
        @Schema(description = "활성 관리자 수") long activeAdmins,
        @Schema(description = "정지 관리자 수") long suspendedAdmins,
        @Schema(description = "탈퇴 관리자 수") long withdrawnAdmins
) {
}
