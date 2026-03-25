package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 AiSimilarPostsResponse이다.
 */

@Schema(description = "AI 유사 공고 추천 응답")
public record AiSimilarPostsResponse(
        String analysisId,
        List<String> items
) {
}

