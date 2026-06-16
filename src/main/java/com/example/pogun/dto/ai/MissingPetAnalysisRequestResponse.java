package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "실종 공고 AI 분석 요청 응답")
public record MissingPetAnalysisRequestResponse(
        UUID analysisId,
        UUID noticeId,
        String status,
        Integer remainingCredits,
        Integer appliedMaxDescriptionLength,
        String purchaseId,
        String productId
) {
}
