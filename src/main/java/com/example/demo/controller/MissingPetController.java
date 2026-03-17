package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.MissingPetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

import java.util.Map;

@RestController
@RequestMapping("/api/missing-pets")
@Tag(name = "Missing Pets", description = "실종 반려동물 공고 API")
@RequiredArgsConstructor
public class MissingPetController {

    private final MissingPetService missingPetService;

    @GetMapping
    @Operation(summary = "실종 동물 공고 목록 조회", description = "사용자가 작성한 실종 동물 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Map<String, Object> result = missingPetService.getMissingPetList(region, breed, status, from, to, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 동물 공고 목록 조회 성공", result));
    }

    @PostMapping
    @Operation(summary = "실종 공고 생성", description = "사용자가 새로운 실종 동물 공고를 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = missingPetService.createMissingPet(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "행방불명 공고 생성 성공", data));
    }

    @GetMapping("/{missingPetId}")
    @Operation(summary = "실종 공고 상세 조회", description = "실종 동물 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String missingPetId) {
        Map<String, Object> data = missingPetService.getMissingPetDetail(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상세 조회 성공", data));
    }

    @PatchMapping("/{missingPetId}")
    @Operation(summary = "실종 공고 수정", description = "실종 동물 공고를 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String missingPetId, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = missingPetService.updateMissingPet(missingPetId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 수정 성공", data));
    }

    @PatchMapping("/{missingPetId}/status")
    @Operation(summary = "실종 공고 상태 변경", description = "실종 동물 행방불명 공고의 상태를 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String missingPetId, @RequestBody Map<String, String> request) {
        Map<String, Object> data = missingPetService.changeMissingPetStatus(missingPetId, request.get("status"));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상태 변경 성공", data));
    }

    @PostMapping("/{missingPetId}/view")
    @Operation(summary = "실종 공고 조회수 증가", description = "실종 동물 공고의 조회수를 증가시킵니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> increaseView(@PathVariable String missingPetId) {
        Map<String, Object> data = missingPetService.increaseMissingPetView(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", data));
    }
}
