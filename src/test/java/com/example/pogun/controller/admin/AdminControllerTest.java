package com.example.pogun.controller.admin;

import com.example.pogun.dto.admin.AdminDashboardResponse;
import com.example.pogun.dto.admin.AdminDeleteResponse;
import com.example.pogun.dto.admin.AdminUserSanctionRequest;
import com.example.pogun.dto.admin.AdminUserSanctionResponse;
import com.example.pogun.dto.admin.AdminVisibilityResponse;
import com.example.pogun.dto.admin.AdminVisibilityUpdateRequest;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.dto.report.ReportStatusUpdateRequest;
import com.example.pogun.service.admin.AdminService;
import com.example.pogun.service.report.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AdminService adminService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    @DisplayName("대시보드 조회 성공")
    void dashboardSuccess() throws Exception {
        given(adminService.getDashboard()).willReturn(new AdminDashboardResponse(9L, 4L, 2L, 1L, 0L));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대시보드 통계 조회 성공"))
                .andExpect(jsonPath("$.data.todayReports").value(9))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 공개 상태 변경 성공")
    void updateCommunityVisibilitySuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440040");
        AdminVisibilityUpdateRequest request = new AdminVisibilityUpdateRequest();
        request.setVisibility("VISIBLE");
        given(adminService.updateCommunityVisibility(postId.toString(), "VISIBLE"))
                .willReturn(new AdminVisibilityResponse(postId, "VISIBLE", "ACTIVE", null));

        mockMvc.perform(patch("/api/admin/community/posts/{postId}/visibility", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 게시글 공개 상태 변경 성공"))
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 공개 상태 변경 성공")
    void updateMissingPostVisibilitySuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440041");
        AdminVisibilityUpdateRequest request = new AdminVisibilityUpdateRequest();
        request.setVisibility("VISIBLE");
        given(adminService.updateMissingPostVisibility(postId.toString(), "VISIBLE"))
                .willReturn(new AdminVisibilityResponse(postId, "VISIBLE", null, false));

        mockMvc.perform(patch("/api/admin/posts/{postId}/visibility", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 공개 상태 변경 성공"))
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"))
                .andDo(print());
    }

    @Test
    @DisplayName("사용자 제재 성공")
    void sanctionUserSuccess() throws Exception {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440042");
        AdminUserSanctionRequest request = new AdminUserSanctionRequest();
        request.setAction("BAN");
        given(adminService.sanctionUser(userId.toString(), "BAN"))
                .willReturn(new AdminUserSanctionResponse(userId, "BAN", "BANNED"));

        mockMvc.perform(patch("/api/admin/users/{userId}/sanctions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사용자 제재 처리 성공"))
                .andExpect(jsonPath("$.data.action").value("BAN"))
                .andExpect(jsonPath("$.data.status").value("BANNED"))
                .andDo(print());
    }

    @Test
    @DisplayName("신고 목록 조회 성공")
    void adminReportsSuccess() throws Exception {
        UUID reportId = UUID.fromString("550e8400-e29b-41d4-a716-446655440043");
        given(reportService.getAllReports()).willReturn(List.of(
                new ReportResponse(reportId, UUID.randomUUID(), "COMMUNITY_POST", UUID.randomUUID(), "SPAM", null, "RECEIVED", null, Instant.parse("2026-03-20T10:00:00Z"))
        ));

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("신고 내역 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value(reportId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("신고 상태 변경 성공")
    void updateReportStatusSuccess() throws Exception {
        UUID reportId = UUID.fromString("550e8400-e29b-41d4-a716-446655440044");
        ReportStatusUpdateRequest request = new ReportStatusUpdateRequest();
        request.setStatus("REVIEWING");
        given(reportService.updateReportStatus(reportId.toString(), "REVIEWING")).willReturn(
                new ReportResponse(reportId, UUID.randomUUID(), "COMMUNITY_POST", UUID.randomUUID(), "SPAM", null, "REVIEWING", null, Instant.parse("2026-03-20T10:00:00Z"))
        );

        mockMvc.perform(patch("/api/admin/reports/{reportId}", reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("신고 처리 상태 변경 성공"))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 강제 삭제 성공")
    void adminDeletePostSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440045");
        given(adminService.deleteMissingPost(postId.toString()))
                .willReturn(new AdminDeleteResponse(postId, true, null));

        mockMvc.perform(delete("/api/admin/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 강제 삭제 성공")
    void adminDeleteCommunityPostSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440046");
        given(adminService.deleteCommunityPost(postId.toString()))
                .willReturn(new AdminDeleteResponse(postId, true, "DELETED"));

        mockMvc.perform(delete("/api/admin/community/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }
}