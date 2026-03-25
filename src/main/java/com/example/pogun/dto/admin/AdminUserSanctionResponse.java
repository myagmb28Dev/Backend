package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 AdminUserSanctionResponse이다.
 */

@Schema(description = "사용자 제재 응답")
public record AdminUserSanctionResponse(UUID userId, String action, String status) {
}
