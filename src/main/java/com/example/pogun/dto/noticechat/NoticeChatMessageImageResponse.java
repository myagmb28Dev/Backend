package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "채팅 메시지 첨부 미디어 응답")
public record NoticeChatMessageImageResponse(
        @Schema(description = "첨부 ID") UUID id,
        @Schema(description = "표시 URL") String imageUrl,
        @Schema(description = "원본 URL") String originalUrl,
        @Schema(description = "WEBP URL") String webpUrl,
        @Schema(description = "중간 크기 URL") String mediumUrl,
        @Schema(description = "썸네일 URL") String thumbnailUrl,
        @Schema(description = "저화질 프리뷰 URL") String previewUrl,
        @Schema(description = "콘텐츠 타입") String contentType,
        @Schema(description = "표시 순서") int displayOrder
) {
}
