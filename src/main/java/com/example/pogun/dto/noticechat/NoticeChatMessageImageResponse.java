package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 채팅 이미지 응답 DTO이다.
 */
@Schema(description = "채팅 이미지 응답")
public record NoticeChatMessageImageResponse(
        @Schema(description = "이미지 ID") UUID id,
        @Schema(description = "이미지 URL") String imageUrl,
        @Schema(description = "이미지 순서") int order
) {
}
