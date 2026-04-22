package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * FastAPI가 분석 결과를 콜백으로 전달할 때 사용하는 요청 DTO이다.
 */
@Getter
@Setter
@Schema(description = "AI 분석 결과 콜백 요청")
public class AiAnalysisResultCallbackRequest {

    @NotBlank
    @Schema(description = "분석 상태", example = "SUCCESS")
    private String status;

    @Schema(description = "추정 품종", example = "말티즈")
    private String breed;

    @Schema(description = "추정 색상", example = "white")
    private String color;

    @Schema(description = "분석 특징 목록")
    private List<String> features;

    @Schema(description = "유사 공고 ID 목록")
    private List<String> similarNoticeIds;

    @Schema(description = "신뢰도", example = "0.91")
    private Double confidence;

    @Schema(description = "분석 제공자", example = "fastapi-ai")
    private String provider;

    @Schema(description = "분석 완료 시각", example = "2026-04-22T13:00:00Z")
    private Instant analyzedAt;

    @Schema(description = "분석 실패 메시지", example = "image download failed")
    private String errorMessage;
}
