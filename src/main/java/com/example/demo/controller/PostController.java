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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @GetMapping
    @Operation(summary = "내부 공고 목록 조회", description = "내부(사용자 작성) 실종 공고 목록을 조회합니다. 필터/페이징/정렬을 지원합니다.")
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
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("from", from);
        filters.put("to", to);
        filters.put("sort", sort);
        filters.put("page", page);
        filters.put("size", size);

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
    @Operation(summary = "내부 공고 생성", description = "사용자가 새로운 실종 공고를 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "실종 공고 생성 성공", Map.of("created", request, "id", "missing-post-new")));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "내부 공고 상세 조회", description = "내부 실종 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String postId) {
        Map<String, Object> data = Map.of(
                "id", postId,
                "title", "말티즈 실종",
                "description", "흰색 목줄 착용",
                "rewardAmount", 300000,
                "contactPhone", "010-1111-2222",
                "images", List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상세 조회 성공", data));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "내부 공고 수정", description = "내부 실종 공고를 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 수정 성공", Map.of("id", postId, "updated", request)));
    }

    @PatchMapping("/{postId}/status")
    @Operation(summary = "내부 공고 상태 변경", description = "내부 실종 공고의 상태를 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String postId, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상태 변경 성공", Map.of("id", postId, "status", request.get("status"))));
    }

    @PostMapping("/{postId}/view")
    @Operation(summary = "내부 공고 조회수 증가", description = "내부 공고의 조회수를 증가시킵니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> increaseView(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", Map.of("id", postId, "viewCount", 101)));
    }

    @PostMapping("/{postId}/favorite")
    @Operation(summary = "내부 공고 즐겨찾기 추가", description = "해당 내부 공고를 즐겨찾기에 추가합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFavorite(@PathVariable String postId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "공고 즐겨찾기 추가 성공", Map.of("postId", postId, "bookmarked", true)));
    }

    @DeleteMapping("/{postId}/favorite")
    @Operation(summary = "내부 공고 즐겨찾기 해제", description = "해당 내부 공고를 즐겨찾기에서 제거합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeFavorite(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 즐겨찾기 해제 성공", Map.of("postId", postId, "bookmarked", false)));
    }

    @GetMapping("/favorites")
    @Operation(summary = "내부 즐겨찾기 목록 조회", description = "내가 즐겨찾기한 내부 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> favorites() {
        List<Map<String, Object>> data = List.of(
                Map.of("postId", "missing-post-1", "title", "말티즈 실종"),
                Map.of("postId", "missing-post-7", "title", "고양이 목격")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "즐겨찾기 목록 조회 성공", data));
    }
}




