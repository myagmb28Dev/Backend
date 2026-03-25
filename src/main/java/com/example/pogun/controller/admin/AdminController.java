package com.example.pogun.controller.admin;

import com.example.pogun.dto.admin.AdminDashboardResponse;
import com.example.pogun.dto.admin.AdminDeleteResponse;
import com.example.pogun.dto.admin.AdminUserSanctionRequest;
import com.example.pogun.dto.admin.AdminUserSanctionResponse;
import com.example.pogun.dto.admin.AdminVisibilityResponse;
import com.example.pogun.dto.admin.AdminVisibilityUpdateRequest;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.dto.report.ReportStatusUpdateRequest;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 AdminController이다.
 */

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 대시보드 API")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final ReportService reportService;

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 통계 조회", description = "접수된 신고 건, 숨김 처리된 건, 신고된 사용자 등을 관리자 대시보드를 통해 조회합니다.")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard() {
        // 대시보드는 여러 도메인의 집계값을 한 번에 내려주는 관리자 홈 진입 데이터다.
        AdminDashboardResponse data = adminService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "대시보드 통계 조회 성공", data));
    }

    @PatchMapping("/community/posts/{postId}/visibility")
    @Operation(summary = "커뮤니티 글 숨김/해제 처리", description = "커뮤니티 게시글을 숨김 처리하거나 다시 공개 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<AdminVisibilityResponse>> updateCommunityVisibility(@PathVariable String postId, @Valid @RequestBody AdminVisibilityUpdateRequest request) {
        AdminVisibilityResponse data = adminService.updateCommunityVisibility(postId, request.getVisibility());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 게시글 공개 상태 변경 성공", data));
    }

    @PatchMapping("/posts/{postId}/visibility")
    @Operation(summary = "실종 공고 숨김/해제 처리", description = "실종 공고를 숨김 처리하거나 다시 공개 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<AdminVisibilityResponse>> updateMissingPostVisibility(@PathVariable String postId, @Valid @RequestBody AdminVisibilityUpdateRequest request) {
        AdminVisibilityResponse data = adminService.updateMissingPostVisibility(postId, request.getVisibility());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 공개 상태 변경 성공", data));
    }

    @PatchMapping("/users/{userId}/sanctions")
    @Operation(summary = "사용자 제재", description = "특정 사용자에게 이용 정지 등 관리자 제재를 적용합니다.")
    public ResponseEntity<ApiResponse<AdminUserSanctionResponse>> sanctionUser(@PathVariable String userId, @Valid @RequestBody AdminUserSanctionRequest request) {
        AdminUserSanctionResponse data = adminService.sanctionUser(userId, request.getAction());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 제재 처리 성공", data));
    }

    @GetMapping("/reports")
    @Operation(summary = "전체 신고 내역 조회", description = "사용자들이 접수한 모든 신고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> adminReports() {
        List<ReportResponse> data = reportService.getAllReports();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 내역 조회 성공", data));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "신고 처리 상태 업데이트", description = "특정 신고 건의 진행 상태(접수, 검토 중, 처리 완료 등)를 변경합니다.")
    public ResponseEntity<ApiResponse<ReportResponse>> updateReportStatus(@PathVariable String reportId, @Valid @RequestBody ReportStatusUpdateRequest request) {
        // 상태 변경은 관리자 검토 흐름의 핵심이라, 잘못된 상태값 검증도 서비스에서 엄격하게 처리한다.
        ReportResponse data = reportService.updateReportStatus(reportId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 처리 상태 변경 성공", data));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "실종 공고 강제 삭제", description = "관리자 권한으로 실종 공고를 완전히 삭제합니다.")
    public ResponseEntity<ApiResponse<AdminDeleteResponse>> adminDeletePost(@PathVariable String postId) {
        // 강제 삭제는 채팅방, 메시지, 북마크 같은 연관 데이터 정리까지 포함한 파괴적 작업이다.
        AdminDeleteResponse data = adminService.deleteMissingPost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 삭제 성공", data));
    }

    @DeleteMapping("/community/posts/{postId}")
    @Operation(summary = "커뮤니티 글 강제 삭제", description = "관리자 권한으로 커뮤니티 게시글을 완전히 삭제합니다.")
    public ResponseEntity<ApiResponse<AdminDeleteResponse>> adminDeleteCommunityPost(@PathVariable String postId) {
        AdminDeleteResponse data = adminService.deleteCommunityPost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", data));
    }
}
