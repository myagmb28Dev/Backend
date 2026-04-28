package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "관리자 세션 상태 응답")
public record AdminSessionStateResponse(
        boolean authenticated,
        String stage,
        AdminAuthSessionAdminResponse admin,
        List<String> permissions,
        Instant expiresAt
) {
}
