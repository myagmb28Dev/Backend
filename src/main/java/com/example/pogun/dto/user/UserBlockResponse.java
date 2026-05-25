package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "차단 사용자 응답")
public record UserBlockResponse(
        UUID userId,
        String nickname,
        String profileImageUrl,
        Instant blockedAt
) {
}

