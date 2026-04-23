package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 채팅 이미지 응답 DTO이다.
 */
@Schema(description = "채팅 이미지 응답")
public record NoticeChatMessageImageResponse(
        @Schema(description = "이미지 ID") UUID id,
        @Schema(description = "대표 이미지 URL") String imageUrl,
        @Schema(description = "원본 URL") String originalUrl,
        @Schema(description = "WEBP 원본 표시 URL") String webpUrl,
        @Schema(description = "중간 크기 URL") String mediumUrl,
        @Schema(description = "썸네일 URL") String thumbnailUrl,
        @Schema(description = "저화질 프리뷰 URL") String previewUrl,
        @Schema(description = "이미지 순서") int order
) {
}
