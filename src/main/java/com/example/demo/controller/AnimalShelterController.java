package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shelter/animals")
public class AnimalShelterController {

    @GetMapping
    @Operation(summary = "전국 동물 공고 조회", description = "외부 API로부터 전국 유기동물 공고를 조회하고 정규화된 결과를 반환합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);

        List<Map<String, Object>> items = List.of(
                Map.of("id", "ext-1", "region", "서울", "breed", "믹스"),
                Map.of("id", "ext-2", "region", "경기", "breed", "푸들")
        );
        Map<String, Object> data = Map.of(
                "source", "KOREA_ANIMAL_PROTECTION_API",
                "filters", filters,
                "items", items
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "전국 동물 공고 조회 성공", data));
    }

    @GetMapping("/{externalId}")
    @Operation(summary = "전국 동물 상세 조회", description = "외부 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String externalId) {
        Map<String, Object> data = Map.of(
                "id", externalId,
                "specialNote", "사람을 잘 따름",
                "contact", "02-000-0000",
                "inquiryEnabled", true
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "전국 동물 상세 조회 성공", data));
    }

    @PostMapping("/{externalId}/inquiries")
    @Operation(summary = "유기동물 문의 전달", description = "해당 외부 공고에 대해 문의를 전달합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createInquiry(
            @PathVariable String externalId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED,
                "유기동물 문의 전달 성공",
                Map.of("externalId", externalId, "message", request.getOrDefault("message", "문의 내용"))
        ));
    }
}

