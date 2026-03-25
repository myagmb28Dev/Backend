package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 UserProfileResponse이다.
 */

@Schema(description = "사용자 프로필 응답")
public record UserProfileResponse(
        @Schema(description = "사용자 ID") UUID id,
        @Schema(description = "이메일") String email,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "전화번호") String phoneNumber,
        @Schema(description = "대표 로그인 제공자") String provider,
        @Schema(description = "연결된 제공자 목록") List<String> linkedProviders,
        @Schema(description = "역할") String role,
        @Schema(description = "상태") String status
) {
}
