package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPostDetailResponse이다.
 */

@Schema(description = "커뮤니티 글 상세 응답")
public record CommunityPostDetailResponse(
        UUID id,
        String title,
        String content,
        String category,
        List<String> tags,
        String authorNickname,
        Long viewCount,
        Long likeCount,
        Instant createdAt,
        CommunityPollResponse poll,
        List<String> imageUrls
) {
}


