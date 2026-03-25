package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 SocialUnlinkResponse이다.
 */

@Schema(description = "소셜 계정 연동 해제 응답")
public record SocialUnlinkResponse(
        @Schema(description = "제공자") String provider,
        @Schema(description = "해제 여부") boolean unlinked,
        @Schema(description = "남아 있는 연결 제공자 목록") List<String> linkedProviders,
        @Schema(description = "응답 메시지") String message
) {
}
