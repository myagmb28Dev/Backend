package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 AiAnalysisResultResponse이다.
 */

@Schema(description = "AI 분석 결과 응답")
public record AiAnalysisResultResponse(
        String analysisId,
        String status,
        List<String> features
) {
}

