package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pet-notices")
public class PetNoticeController {

    @GetMapping
    @Operation(summary = "외부 공고 목록 조회", description = "전국 동물 공고(외부 제공)를 조회합니다. 필터와 정렬을 지원합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "LATEST") String sort
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("sort", sort);

        List<Map<String, Object>> data = List.of(
                Map.of("id", "notice-1", "title", "말티즈 실종", "missingRegion", "서울", "status", "OPEN"),
                Map.of("id", "notice-2", "title", "푸들 발견", "missingRegion", "부산", "status", "RESOLVED")
        );
        Map<String, Object> result = Map.of(
                "filters", filters,
                "items", data
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 목록 조회 성공", result));
    }

    @PostMapping
    @Operation(summary = "외부 공고 임시 생성", description = "외부 공고 정보의 임시 생성(주로 테스트용). 실제 프로덕션에서는 외부 연동을 통해 수집됩니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "실종 공고 생성 성공", Map.of("created", request, "id", "notice-new")));
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "외부 공고 상세 조회", description = "외부 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String noticeId) {
        Map<String, Object> data = Map.of(
                "id", noticeId,
                "title", "말티즈 실종",
                "description", "흰색 목줄 착용",
                "rewardAmount", 300000,
                "contactPhone", "010-1111-2222",
                "images", List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상세 조회 성공", data));
    }

    @PatchMapping("/{noticeId}")
    @Operation(summary = "외부 공고 수정", description = "외부 공고 정보를 수정합니다 (관리자 전용).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String noticeId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 수정 성공", Map.of("id", noticeId, "updated", request)));
    }

    @PatchMapping("/{noticeId}/status")
    @Operation(summary = "외부 공고 상태 변경", description = "외부 공고의 상태를 변경합니다 (관리자 전용).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String noticeId, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상태 변경 성공", Map.of("id", noticeId, "status", request.get("status"))));
    }

    @PostMapping("/{noticeId}/views")
    @Operation(summary = "외부 공고 조회수 증가", description = "외부 공고의 조회수를 증가시킵니다 (기록용).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> increaseView(@PathVariable String noticeId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", Map.of("id", noticeId, "viewCount", 101)));
    }

    @PostMapping("/{noticeId}/analyze-image")
    @Operation(summary = "외부 공고 사진 분석 요청", description = "외부 공고에 첨부된 사진의 특징 분석을 요청합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeImage(@PathVariable String noticeId) {
        Map<String, Object> data = Map.of(
                "noticeId", noticeId,
                "analysisStatus", "SUCCESS",
                "features", List.of("흰색", "소형견", "귀가 접힘"),
                "similarNotices", List.of("notice-3", "notice-4")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "동물 사진 특징 분석 완료", data));
    }
}


