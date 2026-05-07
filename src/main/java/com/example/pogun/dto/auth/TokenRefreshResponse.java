package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Firebase 토큰 리프레시 응답")
public record TokenRefreshResponse(
        @Schema(description = "새 Firebase ID Token") String idToken,
        @Schema(description = "새 Refresh Token") String refreshToken,
        @Schema(description = "ID Token 만료까지 남은 초", example = "3600") long expiresIn,
        @Schema(description = "Firebase UID") String firebaseUid,
        @Schema(description = "Firebase 프로젝트 ID") String projectId
) {
}

