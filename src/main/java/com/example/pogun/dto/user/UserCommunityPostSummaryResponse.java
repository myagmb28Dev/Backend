package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 UserCommunityPostSummaryResponse이다.
 */

@Schema(description = "내 커뮤니티 글 요약 응답")
public record UserCommunityPostSummaryResponse(
        @Schema(description = "게시글 ID") UUID postId,
        @Schema(description = "제목") String title,
        @Schema(description = "상태") String status,
        @Schema(description = "조회수") Long viewCount,
        @Schema(description = "생성 시각") Instant createdAt
) {
}
