package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "관리자 세션 사용자 정보")
public record AdminAuthSessionAdminResponse(
        UUID id,
        String email,
        String name,
        String role
) {
}
