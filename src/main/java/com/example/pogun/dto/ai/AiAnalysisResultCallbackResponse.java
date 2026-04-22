package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 분석 결과 콜백 저장 후 반환하는 응답 DTO이다.
 */
@Schema(description = "AI 분석 결과 콜백 저장 응답")
public record AiAnalysisResultCallbackResponse(
        String analysisId,
        String targetType,
        String targetId,
        String status
) {
}
