package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "채팅 이미지 원본 다운로드 응답")
public record NoticeChatImageOriginalResponse(
        @Schema(description = "이미지 ID") UUID imageId,
        @Schema(description = "원본 다운로드 URL") String originalUrl
) {
}
