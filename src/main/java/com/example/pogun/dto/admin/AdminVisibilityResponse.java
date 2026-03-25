package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 AdminVisibilityResponse이다.
 */

@Schema(description = "관리자 공개 상태 변경 응답")
public record AdminVisibilityResponse(UUID postId, String visibility, String status, Boolean hidden) {
}
