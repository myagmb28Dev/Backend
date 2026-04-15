package com.example.pogun.dto.noticechat;

import java.util.UUID;

/**
 * 메시지 첨부 조회에서 필요한 이미지 컬럼만 가져오는 projection이다.
 */
public record NoticeChatMessageImageProjection(
        UUID messageId,
        UUID id,
        String imageUrl,
        String webpUrl,
        String mediumUrl,
        String thumbnailUrl,
        String previewUrl,
        int displayOrder
) {
}
