package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "관리자 세션 응답")
public record AdminAuthSessionResponse(
        String accessToken,
        Instant expiresAt,
        String stage,
        AdminAuthSessionAdminResponse admin,
        List<String> permissions
) {
}
