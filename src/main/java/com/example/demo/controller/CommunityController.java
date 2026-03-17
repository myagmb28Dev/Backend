package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Community", description = "커뮤니티 API")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    @Operation(summary = "커뮤니티 글 목록 조회", description = "커뮤니티 글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false, defaultValue = "LATEST") String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q
    ) {
        Map<String, Object> data = communityService.getPostList(type, category, tag, q);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }

    @PostMapping
    @Operation(summary = "커뮤니티 글 생성", description = "커뮤니티 글을 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = communityService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "커뮤니티 글 생성 성공", data));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 상세 조회", description = "커뮤니티 글의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String postId) {
        Map<String, Object> data = communityService.getPostDetail(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 상세 조회 성공", data));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 수정", description = "커뮤니티 글을 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = communityService.updatePost(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 수정 성공", data));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 삭제", description = "커뮤니티 글을 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable String postId) {
        Map<String, Object> data = communityService.deletePost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", data));
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "댓글 목록 조회", description = "지정된 글의 댓글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> comments(@PathVariable String postId) {
        List<Map<String, Object>> data = communityService.getComments(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "댓글 목록 조회 성공", data));
    }

    @PostMapping("/{postId}/comments")
    @Operation(summary = "댓글 작성", description = "지정된 글에 댓글을 작성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createComment(@PathVariable String postId, @RequestBody Map<String, String> request) {
        Map<String, Object> data = communityService.createComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "댓글 작성 성공", data));
    }

    @PostMapping("/{postId}/votes")
    @Operation(summary = "투표 참여", description = "지정된 글의 투표에 참여합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vote(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = communityService.vote(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "투표 참여 성공", data));
    }

    @PostMapping("/{postId}/reactions")
    @Operation(summary = "좋아요/반응", description = "지정된 글에 좋아요나 사용자 반응을 추가합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> react(@PathVariable String postId, @RequestBody Map<String, String> request) {
        Map<String, Object> data = communityService.react(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "좋아요/반응 처리 성공", data));
    }
}


