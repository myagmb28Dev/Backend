package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "PassKey 옵션 응답")
public record AdminPasskeyOptionsResponse(
        UUID challengeId,
        Object publicKey
) {
}
