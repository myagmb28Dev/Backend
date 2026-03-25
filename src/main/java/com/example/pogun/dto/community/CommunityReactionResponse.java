package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityReactionResponse이다.
 */

@Schema(description = "커뮤니티 반응 응답")
public record CommunityReactionResponse(
        UUID postId,
        String reaction,
        String message
) {
}

