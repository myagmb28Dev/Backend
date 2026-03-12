package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @PostMapping("/photos")
    @Operation(summary = "사진 업로드", description = "분석용 사진을 업로드합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadPhoto(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED,
                "분석용 사진 업로드 성공",
                Map.of("photoId", "photo-1", "fileName", request.getOrDefault("fileName", "sample.jpg"))
        ));
    }

    @PostMapping("/analysis")
    @Operation(summary = "사진 분석 요청", description = "업로드된 사진의 분석을 요청합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestAnalysis(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED,
                "사진 분석 요청 성공",
                Map.of("analysisId", "analysis-1", "photoId", request.getOrDefault("photoId", "photo-1"), "status", "PENDING")
        ));
    }

    @GetMapping("/analysis/{analysisId}")
    @Operation(summary = "분석 결과 조회", description = "사진 분석 결과를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalysis(@PathVariable String analysisId) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "분석 결과 조회 성공",
                Map.of("analysisId", analysisId, "status", "SUCCESS", "features", List.of("흰색", "소형견", "귀가 접힘"))
        ));
    }

    @GetMapping("/analysis/{analysisId}/similar-posts")
    @Operation(summary = "유사 공고 추천", description = "분석 결과를 기반으로 유사 공고를 추천합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSimilarPosts(@PathVariable String analysisId) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "유사 공고 추천 조회 성공",
                Map.of("analysisId", analysisId, "items", List.of("missing-post-3", "missing-post-4"))
        ));
    }

    @PostMapping("/analysis/{analysisId}/retry")
    @Operation(summary = "분석 재시도", description = "분석 실패 시 재시도 요청을 보냅니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryAnalysis(@PathVariable String analysisId) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "분석 재시도 요청 성공",
                Map.of("analysisId", analysisId, "status", "RETRYING")
        ));
    }
}

