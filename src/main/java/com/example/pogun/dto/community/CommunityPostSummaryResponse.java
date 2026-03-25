package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPostSummaryResponse이다.
 */

@Schema(description = "커뮤니티 글 요약 응답")
public record CommunityPostSummaryResponse(
        UUID id,
        String title,
        String category,
        List<String> tags,
        String authorNickname,
        Long viewCount,
        Long likeCount,
        Instant createdAt
) {
}
