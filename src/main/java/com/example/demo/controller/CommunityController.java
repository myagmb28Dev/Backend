package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityController {

    @GetMapping
    @Operation(summary = "커뮤니티 글 목록 조회", description = "커뮤니티 글 목록을 조회합니다. 카테고리/태그/검색을 지원합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false, defaultValue = "LATEST") String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("type", type);
        filters.put("category", category);
        filters.put("tag", tag);
        filters.put("q", q);

        List<Map<String, Object>> items = List.of(
                Map.of("id", "post-1", "title", "강아지 찾았어요", "summary", "제보 감사합니다"),
                Map.of("id", "post-2", "title", "실종 전단지 팁", "summary", "지역 카페 공유 방법")
        );
        Map<String, Object> data = Map.of(
                "filters", filters,
                "items", items
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }

    @PostMapping
    @Operation(summary = "커뮤니티 글 생성", description = "커뮤니티 글을 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "커뮤니티 글 생성 성공", Map.of("id", "post-new", "created", request)));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 상세 조회", description = "커뮤니티 글의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String postId) {
        Map<String, Object> data = Map.of(
                "id", postId,
                "title", "실종 전단지 팁",
                "content", "동네 카페와 SNS를 함께 활용하세요.",
                "poll", Map.of("question", "전단지 배포 경험", "options", List.of("있음", "없음"))
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 상세 조회 성공", data));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 수정", description = "커뮤니티 글을 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 수정 성공", Map.of("id", postId, "updated", request)));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 삭제", description = "커뮤니티 글을 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", Map.of("id", postId, "deleted", true)));
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "댓글 목록 조회", description = "지정된 글의 댓글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> comments(@PathVariable String postId) {
        List<Map<String, Object>> data = List.of(
                Map.of("id", "c1", "postId", postId, "content", "좋은 정보 감사합니다."),
                Map.of("id", "c2", "postId", postId, "content", "저도 같은 방법 썼어요.")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "댓글 목록 조회 성공", data));
    }

    @PostMapping("/{postId}/comments")
    @Operation(summary = "댓글 작성", description = "지정된 글에 댓글을 작성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createComment(@PathVariable String postId, @RequestBody Map<String, String> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "댓글 작성 성공", Map.of("postId", postId, "content", request.get("content"))));
    }

    @PostMapping("/{postId}/votes")
    @Operation(summary = "투표 참여", description = "지정된 글의 투표에 참여합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vote(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "투표 참여 성공", Map.of("postId", postId, "selection", request.get("option"))));
    }

    @PostMapping("/{postId}/reactions")
    @Operation(summary = "좋아요/반응", description = "지정된 글에 좋아요나 사용자 반응을 추가합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> react(@PathVariable String postId, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "좋아요/반응 처리 성공", Map.of("postId", postId, "reaction", request.get("reaction"))));
    }
}


