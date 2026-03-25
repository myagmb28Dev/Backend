package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 LogoutResponse이다.
 */

@Schema(description = "로그아웃 응답")
public record LogoutResponse(
        @Schema(description = "세션 무효화 여부") boolean revoked,
        @Schema(description = "응답 메시지") String message
) {
}