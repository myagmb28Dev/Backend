package com.example.demo.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "이미지 분석 AI API")
public class AiController {

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "사진 업로드", description = "분석용 사진을 multipart/form-data로 업로드합니다. 허용 확장자: .png, .jpg; 최대 5MB")
    public ResponseEntity<ApiResponse<?>> uploadPhoto(
            @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "파일이 첨부되지 않았습니다.", null));
        }

        long maxBytes = 5L * 1024L * 1024L; // 5MB
        if (file.getSize() > maxBytes) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "파일 크기가 5MB를 초과합니다.", Map.of("maxSizeBytes", maxBytes, "size", file.getSize())));
        }

        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        }
        Set<String> allowed = Set.of(".png", ".jpg", ".jpeg");
        if (!allowed.contains(ext)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "허용되지 않는 파일 확장자입니다.", Map.of("allowed", allowed, "ext", ext)));
        }

        Map<String, Object> resp = Map.of(
                "photoId", "photo-" + System.currentTimeMillis(),
                "fileName", filename,
                "size", file.getSize(),
                "contentType", file.getContentType()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED, "분석용 사진 업로드 성공", resp));
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

