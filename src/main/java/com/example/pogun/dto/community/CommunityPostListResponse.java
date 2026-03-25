package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPostListResponse이다.
 */

@Schema(description = "커뮤니티 글 목록 응답")
public record CommunityPostListResponse(
        long totalElements,
        int totalPages,
        List<CommunityPostSummaryResponse> items
) {
}

