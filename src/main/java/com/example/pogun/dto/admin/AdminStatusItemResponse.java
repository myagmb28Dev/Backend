package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "관리자 상태 상세 항목")
public record AdminStatusItemResponse(
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "역할") String role,
        @Schema(description = "상태") String status,
        @Schema(description = "권한 목록") List<String> permissions,
        @Schema(description = "관리자 이메일 재인증 필요 여부") boolean adminEmailVerificationRequired,
        @Schema(description = "관리자 이메일 인증 완료 시각") Instant adminEmailVerifiedAt,
        @Schema(description = "관리자 이메일 인증 상태", allowableValues = {"PENDING","VERIFIED"}) String adminEmailVerificationStatus
) {
}
