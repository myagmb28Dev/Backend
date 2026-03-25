package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 AiAnalysisCreateResponse이다.
 */

@Schema(description = "AI 분석 요청 응답")
public record AiAnalysisCreateResponse(
        String analysisId,
        String photoId,
        String status
) {
}
