package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * API 요청/응답 데이터 전송 객체인 AdminPromoteResponse이다.
 */
@Schema(description = "관리자 승격 응답")
public record AdminPromoteResponse(
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "변경 후 역할") String role,
        @Schema(description = "계정 상태") String status
) {
}
