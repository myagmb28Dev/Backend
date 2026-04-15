package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.pogun.dto.location.RegionResponse;

import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 AuthResponse이다.
 */

@Schema(description = "로그인 응답")
public record AuthResponse(
        @Schema(description = "사용자 ID") UUID id,
        @Schema(description = "온보딩 대기 ID") UUID pendingSignupId,
        @Schema(description = "Firebase UID") String firebaseUid,
        @Schema(description = "이메일") String email,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "대표 로그인 제공자") String provider,
        @Schema(description = "연결된 제공자 목록") List<String> linkedProviders,
        @Schema(description = "역할") String role,
        @Schema(description = "회원가입 상태", allowableValues = {"COMPLETED", "PENDING_ONBOARDING"}) String registrationStatus,
        @Schema(description = "지역") String region,
        @Schema(description = "행정구역 정보") RegionResponse regionInfo
) {
}
