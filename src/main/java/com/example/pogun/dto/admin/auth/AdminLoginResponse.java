package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 로그인 응답")
public record AdminLoginResponse(
        String nextStep,
        boolean requiresPassKey,
        AdminAuthSessionResponse session,
        AdminPasskeyOptionsResponse passkey
) {
}
