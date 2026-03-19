package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Admin", description = "관리자 대시보드 API")
@RequiredArgsConstructor
public class AdminController {
    private final ReportService reportService;

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 통계 조회", description = "접수된 신고 건, 숨김 처리된 건, 신고된 사용자 등을 관리자 대시보드를 통해 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        Map<String, Object> data = Map.of(
                "todayReports", 9,
                "pendingReports", 4,
                "hiddenCommunityPosts", 3,
                "hiddenMissingPosts", 2,
                "sanctionedUsers", 1
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "대시보드 통계 조회 성공", data));
    }

    @PatchMapping("/community/posts/{postId}/visibility")
    @Operation(summary = "커뮤니티 글 숨김/해제 처리", description = "커뮤니티 게시글을 숨김 처리하거나 다시 공개 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCommunityVisibility(
            @PathVariable String postId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "커뮤니티 게시글 공개 상태 변경 성공",
                Map.of("postId", postId, "visibility", request.getOrDefault("visibility", "HIDDEN"))
        ));
    }

    @PatchMapping("/posts/{postId}/visibility")
    @Operation(summary = "실종 공고 숨김/해제 처리", description = "실종 공고를 숨김 처리하거나 다시 공개 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMissingPostVisibility(
            @PathVariable String postId,
            @RequestBody Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "실종 공고 공개 상태 변경 성공",
                Map.of("postId", postId, "visibility", request.getOrDefault("visibility", "HIDDEN"))
        ));
    }

    @PatchMapping("/users/{userId}/sanctions")
    @Operation(summary = "사용자 제재", description = "특정 사용자에게 이용 정지 등 관리자 제재를 적용합니다.")
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
    @Operation(summary = "전체 신고 내역 조회", description = "사용자들이 접수한 모든 신고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminReports() {
        List<Map<String, Object>> data = reportService.getAllReports();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 내역 조회 성공", data));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "신고 처리 상태 업데이트", description = "특정 신고 건의 진행 상태(접수, 검토 중, 처리 완료 등)를 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReportStatus(@PathVariable String reportId, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = reportService.updateReportStatus(reportId, request.get("status"));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 처리 상태 변경 성공", data));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "실종 공고 강제 삭제", description = "관리자 권한으로 실종 공고를 완전히 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDeletePost(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 삭제 성공", Map.of("postId", postId, "deleted", true)));
    }

    @DeleteMapping("/community/posts/{postId}")
    @Operation(summary = "커뮤니티 글 강제 삭제", description = "관리자 권한으로 커뮤니티 게시글을 완전히 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDeleteCommunityPost(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", Map.of("postId", postId, "deleted", true)));
    }
}
