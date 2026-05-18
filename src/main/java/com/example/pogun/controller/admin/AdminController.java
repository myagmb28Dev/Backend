package com.example.pogun.controller.admin;

import com.example.pogun.dto.admin.AdminDashboardResponse;
import com.example.pogun.dto.admin.AdminDeleteResponse;
import com.example.pogun.dto.admin.AdminNotificationSendRequest;
import com.example.pogun.dto.admin.AdminPromoteByEmailRequest;
import com.example.pogun.dto.admin.AdminPromoteRequest;
import com.example.pogun.dto.admin.AdminPromoteResponse;
import com.example.pogun.dto.admin.AdminReportActionRequest;
import com.example.pogun.dto.admin.AdminReportReviewRequest;
import com.example.pogun.dto.admin.AdminStatusResponse;
import com.example.pogun.dto.admin.AdminUserRoleUpdateRequest;
import com.example.pogun.dto.admin.AdminUserSanctionRequest;
import com.example.pogun.dto.admin.AdminUserSanctionResponse;
import com.example.pogun.dto.admin.AdminUserStatusUpdateRequest;
import com.example.pogun.dto.admin.AdminVisibilityResponse;
import com.example.pogun.dto.admin.AdminVisibilityUpdateRequest;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.dto.report.ReportStatusUpdateRequest;
import com.example.pogun.service.admin.AdminConsoleService;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.admin.AdminTrafficLogService;
import com.example.pogun.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 콘솔 API")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AdminConsoleService adminConsoleService;
    private final AdminTrafficLogService adminTrafficLogService;
    private final ReportService reportService;

    @GetMapping("/dashboard")
    @Operation(summary = "기존 대시보드 통계 조회", description = "기존 관리자 홈 집계 응답을 조회합니다.")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard() {
        AdminDashboardResponse data = adminService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "대시보드 통계 조회 성공", data));
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "대시보드 요약", description = "기간 기준 KPI, 최근 신고, 최근 알림 실패를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboardSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Map<String, Object> data = adminConsoleService.dashboardSummary(from, to);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "대시보드 요약 조회 성공", data));
    }

    @GetMapping("/dashboard/timeline")
    @Operation(summary = "대시보드 타임라인", description = "기간별 공고, 신고, 가입 추이를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboardTimeline(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "day") String granularity
    ) {
        Map<String, Object> data = adminConsoleService.dashboardTimeline(from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "대시보드 타임라인 조회 성공", data));
    }

    @GetMapping("/dashboard/priorities")
    @Operation(summary = "우선 처리 항목", description = "누적 신고 수 기준 우선 검토 대상을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> dashboardPriorities() {
        List<Map<String, Object>> data = adminConsoleService.dashboardPriorities();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "우선 처리 항목 조회 성공", data));
    }

    @GetMapping("/users")
    @Operation(summary = "사용자 목록", description = "검색, 상태, 역할, 기간 조건으로 사용자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> users(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        Map<String, Object> data = adminConsoleService.listUsers(query, status, role, createdFrom, createdTo, page, pageSize, sortBy, sortOrder);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 목록 조회 성공", data));
    }

    @GetMapping("/users/export.csv")
    @Operation(summary = "사용자 목록 CSV", description = "검색/필터 조건을 반영한 사용자 목록 CSV를 내려줍니다.")
    public ResponseEntity<byte[]> usersCsv(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "1000") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        Map<String, Object> data = adminConsoleService.listUsers(query, status, role, createdFrom, createdTo, page, pageSize, sortBy, sortOrder);
        return csvResponse("admin-users.csv", extractItems(data));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "사용자 상세", description = "사용자 프로필, 공고, 커뮤니티 활동, 신고 이력을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> userDetail(@PathVariable String userId) {
        Map<String, Object> data = adminConsoleService.userDetail(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 상세 조회 성공", data));
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "사용자 역할 변경", description = "사용자 역할을 USER 또는 ADMIN으로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserRole(@PathVariable String userId, @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        Map<String, Object> data = adminConsoleService.updateUserRole(userId, request.getRole());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 역할 변경 성공", data));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "사용자 상태 변경", description = "사용자 상태를 ACTIVE, SUSPENDED, WITHDRAWN으로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserStatus(@PathVariable String userId, @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        Map<String, Object> data = adminConsoleService.updateUserStatus(userId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 상태 변경 성공", data));
    }

    @PostMapping("/users/{userId}/suspend")
    @Operation(summary = "사용자 정지", description = "사용자를 정지 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspendUser(@PathVariable String userId) {
        Map<String, Object> data = adminConsoleService.updateUserStatus(userId, "SUSPENDED");
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 정지 성공", data));
    }

    @PostMapping("/users/{userId}/unsuspend")
    @Operation(summary = "사용자 정지 해제", description = "사용자를 정상 상태로 복구합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unsuspendUser(@PathVariable String userId) {
        Map<String, Object> data = adminConsoleService.updateUserStatus(userId, "ACTIVE");
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 정지 해제 성공", data));
    }

    @PatchMapping("/users/{userId}/sanctions")
    @Operation(summary = "기존 사용자 제재", description = "기존 제재 API를 유지합니다.")
    public ResponseEntity<ApiResponse<AdminUserSanctionResponse>> sanctionUser(@PathVariable String userId, @Valid @RequestBody AdminUserSanctionRequest request) {
        AdminUserSanctionResponse data = adminService.sanctionUser(userId, request.getAction());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 제재 처리 성공", data));
    }

    @PatchMapping("/users/promote")
    @Operation(summary = "관리자 승격", description = "사용자 ID 기준 기존 사용자를 ADMIN으로 승격합니다.")
    public ResponseEntity<ApiResponse<AdminPromoteResponse>> promoteUser(@Valid @RequestBody AdminPromoteRequest request) {
        AdminPromoteResponse data = adminService.promoteUserToAdmin(request.getUserId());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 승격 성공", data));
    }

    @PostMapping("/users/promote/email")
    @Operation(summary = "관리자 승격(이메일)", description = "이메일 기준으로 사용자를 ADMIN으로 승격합니다. 계정이 없으면 Firebase 사용자 정보로 자동 생성합니다.")
    public ResponseEntity<ApiResponse<AdminPromoteResponse>> promoteUserByEmail(
            @Valid @RequestBody AdminPromoteByEmailRequest request
    ) {
        AdminPromoteResponse data = adminService.promoteUserToAdminByEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 승격 성공", data));
    }

    @GetMapping("/users/permissions/status")
    @Operation(summary = "사용자/관리자 권한 상태 조회", description = "전체 사용자 권한 표시 기준(USER/ADMIN)과 관리자 수 요약, 관리자별 세부 권한 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<AdminStatusResponse>> adminStatus() {
        AdminStatusResponse data = adminService.getAdminStatus();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자/관리자 권한 상태 조회 성공", data));
    }

    @GetMapping("/notices")
    @Operation(summary = "공고 목록", description = "공고 검색, 필터, 페이지네이션 조회를 제공합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> notices(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        Map<String, Object> data = adminConsoleService.listNotices(query, region, breed, status, from, to, page, pageSize, sortBy, sortOrder);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 목록 조회 성공", data));
    }

    @GetMapping("/notices/export.csv")
    @Operation(summary = "공고 목록 CSV", description = "검색/필터 조건을 반영한 공고 목록 CSV를 내려줍니다.")
    public ResponseEntity<byte[]> noticesCsv(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "1000") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        Map<String, Object> data = adminConsoleService.listNotices(query, region, breed, status, from, to, page, pageSize, sortBy, sortOrder);
        return csvResponse("admin-notices.csv", extractItems(data));
    }

    @GetMapping("/notices/{noticeId}")
    @Operation(summary = "공고 상세", description = "관리자용 공고 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> noticeDetail(@PathVariable String noticeId) {
        Map<String, Object> data = adminConsoleService.noticeDetail(noticeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 상세 조회 성공", data));
    }

    @PatchMapping("/notices/{noticeId}")
    @Operation(summary = "공고 수정", description = "관리자 권한으로 공고를 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateNotice(@PathVariable String noticeId, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = adminConsoleService.updateNotice(noticeId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 수정 성공", data));
    }

    @PostMapping("/notices/{noticeId}/hide")
    @Operation(summary = "공고 숨김", description = "관리자 권한으로 공고를 숨김 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hideNotice(@PathVariable String noticeId) {
        Map<String, Object> data = adminConsoleService.hideNotice(noticeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 숨김 성공", data));
    }

    @PostMapping("/notices/{noticeId}/restore")
    @Operation(summary = "공고 복구", description = "숨김 처리된 공고를 다시 공개합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restoreNotice(@PathVariable String noticeId) {
        Map<String, Object> data = adminConsoleService.restoreNotice(noticeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 복구 성공", data));
    }

    @DeleteMapping("/notices/{noticeId}")
    @Operation(summary = "공고 삭제", description = "관리자 권한으로 공고를 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteNotice(@PathVariable String noticeId) {
        Map<String, Object> data = adminConsoleService.deleteNotice(noticeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 삭제 성공", data));
    }

    @PatchMapping("/community/posts/{postId}/visibility")
    @Operation(summary = "커뮤니티 글 숨김/해제 처리", description = "기존 커뮤니티 공개 상태 변경 API를 유지합니다.")
    public ResponseEntity<ApiResponse<AdminVisibilityResponse>> updateCommunityVisibility(@PathVariable String postId, @Valid @RequestBody AdminVisibilityUpdateRequest request) {
        AdminVisibilityResponse data = adminService.updateCommunityVisibility(postId, request.getVisibility());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 게시글 공개 상태 변경 성공", data));
    }

    @GetMapping("/community/posts")
    @Operation(summary = "커뮤니티 글 목록", description = "관리자용 커뮤니티 목록 조회를 제공합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> communityPosts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        Map<String, Object> data = adminConsoleService.listCommunityPosts(query, category, status, page, pageSize, sortBy, sortOrder);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }

    @GetMapping("/community/posts/{postId}")
    @Operation(summary = "커뮤니티 글 상세", description = "관리자용 커뮤니티 글 상세 조회를 제공합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> communityPostDetail(@PathVariable String postId) {
        Map<String, Object> data = adminConsoleService.communityDetail(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 상세 조회 성공", data));
    }

    @DeleteMapping("/community/posts/{postId}")
    @Operation(summary = "커뮤니티 글 삭제", description = "관리자 권한으로 커뮤니티 글을 삭제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDeleteCommunityPost(@PathVariable String postId) {
        Map<String, Object> data = adminConsoleService.deleteCommunityPost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", data));
    }

    @PatchMapping("/posts/{postId}/visibility")
    @Operation(summary = "기존 실종 공고 숨김/해제 처리", description = "기존 관리자 페이지 호환용 공개 상태 변경 API를 유지합니다.")
    public ResponseEntity<ApiResponse<AdminVisibilityResponse>> updateMissingPostVisibility(@PathVariable String postId, @Valid @RequestBody AdminVisibilityUpdateRequest request) {
        AdminVisibilityResponse data = adminService.updateMissingPostVisibility(postId, request.getVisibility());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 공개 상태 변경 성공", data));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "기존 실종 공고 강제 삭제", description = "기존 관리자 페이지 호환용 삭제 API를 유지합니다.")
    public ResponseEntity<ApiResponse<AdminDeleteResponse>> adminDeletePost(@PathVariable String postId) {
        AdminDeleteResponse data = adminService.deleteMissingPost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 삭제 성공", data));
    }

    @GetMapping("/reports")
    @Operation(summary = "신고 목록", description = "상태, 대상 타입, 페이지 기준으로 신고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        Map<String, Object> data = reportService.listAdminReports(status, targetType, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 목록 조회 성공", data));
    }

    @GetMapping("/reports/export.csv")
    @Operation(summary = "신고 목록 CSV", description = "상태/대상 조건을 반영한 신고 목록 CSV를 내려줍니다.")
    public ResponseEntity<byte[]> adminReportsCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "1000") Integer pageSize
    ) {
        Map<String, Object> data = reportService.listAdminReports(status, targetType, page, pageSize);
        return csvResponse("admin-reports.csv", extractItems(data));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "신고 상세", description = "신고 대상, 신고자, 처리 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminReportDetail(@PathVariable String reportId) {
        Map<String, Object> data = reportService.getAdminReportDetail(reportId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 상세 조회 성공", data));
    }

    @GetMapping("/reports/{reportId}/reporters")
    @Operation(summary = "신고자 목록", description = "동일 대상에 대한 신고자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminReporters(@PathVariable String reportId) {
        List<Map<String, Object>> data = reportService.getAdminReporters(reportId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고자 목록 조회 성공", data));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "신고 처리 상태 업데이트", description = "기존 신고 상태 변경 API를 유지합니다.")
    public ResponseEntity<ApiResponse<ReportResponse>> updateReportStatus(@PathVariable String reportId, @Valid @RequestBody ReportStatusUpdateRequest request) {
        ReportResponse data = reportService.updateReportStatus(reportId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 처리 상태 변경 성공", data));
    }

    @PatchMapping("/reports/{reportId}/review")
    @Operation(summary = "신고 검토 처리", description = "기존 검토 API를 유지합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewReport(@PathVariable String reportId, @Valid @RequestBody AdminReportReviewRequest request) {
        reportService.reviewReport(reportId, request);
        Map<String, Object> data = reportService.getAdminReportDetail(reportId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 검토 처리 성공", data));
    }

    @PostMapping("/reports/{reportId}/dismiss")
    @Operation(summary = "신고 반려", description = "신고를 반려 상태로 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dismissReport(@PathVariable String reportId, @RequestBody(required = false) AdminReportActionRequest request) {
        Map<String, Object> data = reportService.dismissReport(reportId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 반려 성공", data));
    }

    @PostMapping("/reports/{reportId}/warn")
    @Operation(summary = "신고 대상 경고", description = "신고 대상 사용자 제재 액션을 적용합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> warnReport(@PathVariable String reportId, @RequestBody(required = false) AdminReportActionRequest request) {
        Map<String, Object> data = reportService.warnReport(reportId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 경고 처리 성공", data));
    }

    @PostMapping("/reports/{reportId}/delete-target")
    @Operation(summary = "신고 대상 삭제", description = "신고 대상 콘텐츠를 삭제하고 신고를 해결 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteReportTarget(@PathVariable String reportId, @RequestBody(required = false) AdminReportActionRequest request) {
        Map<String, Object> data = reportService.deleteTarget(reportId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 대상 삭제 성공", data));
    }

    @PostMapping("/reports/{reportId}/resolve")
    @Operation(summary = "신고 해결", description = "신고를 해결 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveReport(@PathVariable String reportId, @RequestBody(required = false) AdminReportActionRequest request) {
        Map<String, Object> data = reportService.resolveReport(reportId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "신고 해결 성공", data));
    }

    @PostMapping("/notifications/send")
    @Operation(summary = "관리자 알림 발송", description = "전체, 활성 사용자, 특정 사용자에게 수동 알림을 발송합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendNotification(@Valid @RequestBody AdminNotificationSendRequest request) {
        Map<String, Object> data = adminConsoleService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 알림 발송 성공", data));
    }

    @GetMapping("/notifications/history")
    @Operation(summary = "알림 발송 이력", description = "관리자 수동 발송 이력을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> notificationHistory(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        Map<String, Object> data = adminConsoleService.notificationHistory(page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 발송 이력 조회 성공", data));
    }

    @GetMapping("/notifications/history/export.csv")
    @Operation(summary = "알림 발송 이력 CSV", description = "관리자 알림 발송 이력 CSV를 내려줍니다.")
    public ResponseEntity<byte[]> notificationHistoryCsv(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "1000") Integer pageSize
    ) {
        Map<String, Object> data = adminConsoleService.notificationHistory(page, pageSize);
        return csvResponse("admin-notification-history.csv", extractItems(data));
    }

    @GetMapping("/integrations/overview")
    @Operation(summary = "외부 연동 상태 개요", description = "DB, Redis, Firebase, Shelter API 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> integrationOverview(
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh
    ) {
        List<Map<String, Object>> data = adminConsoleService.integrationOverview(forceRefresh);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 연동 상태 조회 성공", data));
    }

    @GetMapping("/services/overview")
    @Operation(summary = "서비스 상태 개요", description = "서비스 전체 상태와 서비스별 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> servicesOverview(
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh
    ) {
        Map<String, Object> data = adminConsoleService.servicesOverview(forceRefresh);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "서비스 상태 개요 조회 성공", data));
    }

    @GetMapping("/integrations/{integrationKey}")
    @Operation(summary = "외부 연동 상세", description = "특정 연동의 최신 상태와 이력을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> integrationDetail(@PathVariable String integrationKey) {
        Map<String, Object> data = adminConsoleService.integrationDetail(integrationKey);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 연동 상세 조회 성공", data));
    }

    @GetMapping("/services/{serviceId}")
    @Operation(summary = "서비스 상태 상세", description = "특정 서비스의 상세 상태와 최근 로그를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> serviceDetail(@PathVariable String serviceId) {
        Map<String, Object> data = adminConsoleService.integrationDetail(serviceId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "서비스 상태 상세 조회 성공", data));
    }

    @PostMapping("/integrations/{integrationKey}/recheck")
    @Operation(summary = "외부 연동 재확인", description = "특정 연동 상태를 즉시 재점검합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recheckIntegration(@PathVariable String integrationKey) {
        Map<String, Object> data = adminConsoleService.recheckIntegration(integrationKey);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 연동 재확인 성공", data));
    }

    @PostMapping("/services/{serviceId}/reboot")
    @Operation(summary = "서비스 재시작 요청", description = "서비스 재확인 작업을 재시작 요청으로 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rebootService(@PathVariable String serviceId) {
        Map<String, Object> data = adminConsoleService.recheckIntegration(serviceId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "서비스 재시작 요청 성공", data));
    }

    @GetMapping("/services/{serviceId}/logs")
    @Operation(summary = "서비스 로그 조회", description = "특정 서비스의 최근 상태 로그를 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> serviceLogs(
            @PathVariable String serviceId,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        Map<String, Object> detail = adminConsoleService.integrationDetail(serviceId);
        Object logs = detail.get("logs");
        List<Map<String, Object>> rows = logs instanceof List<?> list
                ? list.stream()
                .map(this::toStringObjectMap)
                .filter(Objects::nonNull)
                .limit(Math.max(1, limit))
                .toList()
                : List.of();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "서비스 로그 조회 성공", rows));
    }

    private Map<String, Object> toStringObjectMap(Object item) {
        if (!(item instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "감사 로그 목록", description = "관리자 행위 로그를 검색 및 페이지네이션으로 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auditLogs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        Map<String, Object> data = adminConsoleService.auditLogs(query, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "감사 로그 조회 성공", data));
    }

    @GetMapping("/audit-logs/export.csv")
    @Operation(summary = "감사 로그 CSV", description = "검색 조건을 반영한 감사 로그 CSV를 내려줍니다.")
    public ResponseEntity<byte[]> auditLogsCsv(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "1000") Integer pageSize
    ) {
        Map<String, Object> data = adminConsoleService.auditLogs(query, page, pageSize);
        return csvResponse("admin-audit-logs.csv", extractItems(data));
    }

    @GetMapping("/audit-logs/{logId}")
    @Operation(summary = "감사 로그 상세", description = "감사 로그의 변경 전후 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auditLogDetail(@PathVariable String logId) {
        Map<String, Object> data = adminConsoleService.auditLogDetail(logId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "감사 로그 상세 조회 성공", data));
    }

    @GetMapping("/settings")
    @Operation(summary = "관리자 설정 조회", description = "콘솔 운영 설정을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> settings() {
        Map<String, Object> data = adminConsoleService.settings();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 설정 조회 성공", data));
    }

    @PatchMapping("/settings")
    @Operation(summary = "관리자 설정 저장", description = "콘솔 운영 설정을 저장합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSettings(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = adminConsoleService.updateSettings(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 설정 저장 성공", data));
    }

    @GetMapping("/reference-data")
    @Operation(summary = "기준 데이터 요약", description = "관리용 기준 데이터 개수 요약을 제공합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> referenceData() {
        Map<String, Object> data = adminConsoleService.referenceDataSummary();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기준 데이터 요약 조회 성공", data));
    }

    @GetMapping("/reference-data/{kind}")
    @Operation(summary = "기준 데이터 목록", description = "기준 데이터 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> referenceDataList(@PathVariable String kind) {
        List<Map<String, Object>> data = adminConsoleService.referenceDataList(kind);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기준 데이터 목록 조회 성공", data));
    }

    @PostMapping("/reference-data/{kind}")
    @Operation(summary = "기준 데이터 생성", description = "기준 데이터를 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createReferenceData(@PathVariable String kind, @RequestBody Map<String, Object> request) {
        Map<String, Object> data = adminConsoleService.createReferenceData(kind, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기준 데이터 생성 성공", data));
    }

    @PatchMapping("/reference-data/{kind}/{id}")
    @Operation(summary = "기준 데이터 수정", description = "기준 데이터를 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReferenceData(
            @PathVariable String kind,
            @PathVariable String id,
            @RequestBody Map<String, Object> request
    ) {
        Map<String, Object> data = adminConsoleService.updateReferenceData(kind, id, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기준 데이터 수정 성공", data));
    }

    @DeleteMapping("/reference-data/{kind}/{id}")
    @Operation(summary = "기준 데이터 삭제", description = "기준 데이터를 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteReferenceData(
            @PathVariable String kind,
            @PathVariable String id
    ) {
        adminConsoleService.deleteReferenceData(kind, id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기준 데이터 삭제 성공", null));
    }

    @GetMapping("/traffic/logs")
    @Operation(summary = "요청 로그", description = "프로젝트 IN/OUT 요청 로그를 최근순으로 조회합니다.")
        public ResponseEntity<ApiResponse<Map<String, Object>>> trafficLogs(
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "false") Boolean errorsOnly
    ) {
        int resolvedLimit = limit == null ? 100 : limit;
        boolean errors = Boolean.TRUE.equals(errorsOnly);
        List<Map<String, Object>> items = adminTrafficLogService.recent(resolvedLimit, errors);
        Map<String, Object> payload = Map.of(
            "items", items,
            "errorCount", adminTrafficLogService.currentErrorCount()
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "요청 로그 조회 성공", payload));
    }

    @GetMapping("/traffic/config")
    @Operation(summary = "요청 로그 추적 설정", description = "관리자 요청 콘솔에서 표시할 API prefix 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trafficConfig() {
        Map<String, Object> data = Map.of(
                "trackedApiPrefixes", adminTrafficLogService.trackedApiPrefixes(),
                "errorStatusFilter", "status >= 300"
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "요청 로그 설정 조회 성공", data));
    }

    private List<Map<String, Object>> extractItems(Map<String, Object> data) {
        Object items = data.get("items");
        if (items instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<?, ?> source = (Map<?, ?>) item;
                        Map<String, Object> normalized = source.entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> String.valueOf(entry.getKey()),
                                        entry -> (Object) entry.getValue(),
                                        (left, right) -> right,
                                        java.util.LinkedHashMap<String, Object>::new
                                ));
                        return normalized;
                    })
                    .toList();
        }
        return List.of();
    }

    private ResponseEntity<byte[]> csvResponse(String filename, List<Map<String, Object>> rows) {
        String csv = toCsv(rows);
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] payload = csv.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + payload.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(payload, 0, bytes, bom.length, payload.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private String toCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        Set<String> headerSet = new LinkedHashSet<>();
        rows.forEach(row -> headerSet.addAll(row.keySet()));
        List<String> headers = List.copyOf(headerSet);
        String headerLine = headers.stream().map(this::escapeCsv).collect(Collectors.joining(","));
        String body = rows.stream()
                .map(row -> headers.stream()
                        .map(header -> escapeCsv(row.get(header)))
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        return headerLine + "\n" + body + "\n";
    }

    private String escapeCsv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
