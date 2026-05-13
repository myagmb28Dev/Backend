package com.example.pogun.controller.ai;

import com.example.pogun.dto.ai.SimilarNoticeListResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.service.ai.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "AI 분석 보조 API")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiService aiService;

    @GetMapping("/similar")
    @Operation(summary = "유사 공고 조회", description = "targetType과 targetId 기준으로 최신 AI 유사 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<SimilarNoticeListResponse>> getSimilarNotices(
            @RequestParam String targetType,
            @RequestParam String targetId
    ) {
        SimilarNoticeListResponse data = aiService.getLatestSimilarNotices(targetType, targetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유사 공고 조회 성공", data));
    }
}

