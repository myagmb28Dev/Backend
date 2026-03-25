package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 AdminDeleteResponse이다.
 */

@Schema(description = "관리자 삭제 응답")
public record AdminDeleteResponse(UUID postId, boolean deleted, String status) {
}