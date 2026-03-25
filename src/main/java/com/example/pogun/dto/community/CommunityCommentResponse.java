package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityCommentResponse이다.
 */

@Schema(description = "커뮤니티 댓글 응답")
public record CommunityCommentResponse(
        UUID id,
        String content,
        String authorNickname,
        Instant createdAt,
        UUID parentCommentId,
        List<CommunityCommentResponse> replies
) {
}

