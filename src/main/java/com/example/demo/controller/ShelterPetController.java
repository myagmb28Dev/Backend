package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/shelter")
@Tag(name = "Shelter", description = "전국 외부 유기동물 공고 API")
public class ShelterPetController {

    @GetMapping
    @Operation(summary = "유기동물 공고 목록 조회", description = "전국 유기동물 공고 목록을 조회합니다. 필터와 정렬, 페이징을 지원합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("sort", sort);
        filters.put("page", page);
        filters.put("size", size);

        List<Map<String, Object>> items = List.of(
                Map.of("id", "ext-1", "region", "서울", "breed", "믹스", "status", "OPEN"),
                Map.of("id", "ext-2", "region", "경기", "breed", "푸들", "status", "RESOLVED")
        );
        Map<String, Object> data = Map.of(
                "source", "KOREA_ANIMAL_PROTECTION_API",
                "filters", filters,
                "items", items
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "전국 유기 동물 공고 조회 성공", data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "유기동물 공고 상세 조회", description = "외부 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String id) {
        Map<String, Object> data = Map.of(
                "id", id,
                "title", "말티즈 실종",
                "description", "흰색 목줄 착용",
                "rewardAmount", 300000,
                "contactPhone", "010-1111-2222",
                "images", List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유기 동물 공고 상세 조회 성공", data));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "외부 공고 수정", description = "외부 공고 정보를 수정합니다. (관리자 전용)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 공고 수정 성공", Map.of("id", id, "updated", request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "외부 공고 상태 변경", description = "외부 공고의 상태를 변경합니다. (관리자 전용)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 공고 상태 변경 성공", Map.of("id", id, "status", request.get("status"))));
    }

    @PostMapping("/{id}/views")
    @Operation(summary = "외부 공고 조회수 증가", description = "외부 공고의 조회수를 증가시킵니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> increaseView(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", Map.of("id", id, "viewCount", 101)));
    }

    @PostMapping("/{id}/analyze-image")
    @Operation(summary = "외부 공고 사진 분석 요청", description = "공고에 첨부된 사진의 특징 분석을 요청합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeImage(@PathVariable String id) {
        Map<String, Object> analysis = Map.of(
                "noticeId", id,
                "analysisStatus", "SUCCESS",
                "features", List.of("흰색", "소형견", "귀가 접힘"),
                "similarNotices", List.of("notice-3", "notice-4")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "동물 사진 특징 분석 완료", Map.of("analysis", analysis)));
    }
}

