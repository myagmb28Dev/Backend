package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/dashboard")
    @Operation(summary = "관리자 대시보드 조회", description = "관리자 전용 대시보드 정보 조회")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        Map<String, Object> data = Map.of(
                "todayReports", 9,
                "pendingReports", 4,
                "hiddenCommunityPosts", 3,
                "hiddenMissingPosts", 2,
                "sanctionedUsers", 1
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 대시보드 조회 성공", data));
    }

    @PatchMapping("/community/posts/{postId}/visibility")
    @Operation(summary = "커뮤니티 게시글 가시성 변경", description = "관리자가 커뮤니티 게시글의 가시성을 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCommunityVisibility(
            @PathVariable String postId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "커뮤니티 게시글 가시성 변경 성공",
                Map.of("postId", postId, "visibility", request.getOrDefault("visibility", "HIDDEN"))
        ));
    }

    @PatchMapping("/posts/{postId}/visibility")
    @Operation(summary = "실종 공고 가시성 변경", description = "관리자가 실종 공고의 가시성을 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMissingPostVisibility(
            @PathVariable String postId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "실종 공고 가시성 변경 성공",
                Map.of("postId", postId, "visibility", request.getOrDefault("visibility", "HIDDEN"))
        ));
    }

    @PatchMapping("/users/{userId}/sanctions")
    @Operation(summary = "사용자 제재", description = "관리자가 사용자에 대한 제재를 수행합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sanctionUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "사용자 제재 처리 성공",
                Map.of("userId", userId, "action", request.getOrDefault("action", "TEMP_SUSPEND"))
        ));
    }

    @GetMapping("/reports")
    @Operation(summary = "관리자 신고 목록 조회", description = "관리자가 접수된 신고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminReports() {
        List<Map<String, Object>> data = List.of(
                Map.of("id", "report-1", "status", "RECEIVED", "reason", "SPAM"),
                Map.of("id", "report-2", "status", "REVIEWING", "reason", "ABUSE")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 신고 목록 조회 성공", data));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "신고 처리 상태 변경", description = "신고의 처리 상태를 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReportStatus(@PathVariable String reportId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "신고 처리 상태 변경 성공",
                Map.of("reportId", reportId, "status", request.getOrDefault("status", "RESOLVED"))
        ));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "관리자: 실종 공고 삭제", description = "관리자가 실종 공고(내부)를 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDeletePost(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 삭제 성공", Map.of("postId", postId, "deleted", true)));
    }

    @DeleteMapping("/community/posts/{postId}")
    @Operation(summary = "관리자: 커뮤니티 글 삭제", description = "관리자가 커뮤니티 글을 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDeleteCommunityPost(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", Map.of("postId", postId, "deleted", true)));
    }
}

