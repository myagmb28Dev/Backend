package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 WithdrawResponse이다.
 */

@Schema(description = "회원 탈퇴 응답")
public record WithdrawResponse(
        @Schema(description = "회원 상태") String status,
        @Schema(description = "응답 메시지") String message
) {
}