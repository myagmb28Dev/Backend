package com.example.demo.controller;

import com.example.demo.service.ReportService;
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

import java.util.List;
import java.util.Map;

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
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대시보드 통계 조회 성공"))
                .andExpect(jsonPath("$.data.todayReports").value(9))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 공개 상태 변경 성공")
    void updateCommunityVisibilitySuccess() throws Exception {
        Map<String, Object> request = Map.of("visibility", "VISIBLE");

        mockMvc.perform(patch("/api/admin/community/posts/{postId}/visibility", "post-1")
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
        Map<String, Object> request = Map.of("visibility", "VISIBLE");

        mockMvc.perform(patch("/api/admin/posts/{postId}/visibility", "notice-1")
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
        Map<String, Object> request = Map.of("action", "BAN");

        mockMvc.perform(patch("/api/admin/users/{userId}/sanctions", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사용자 제재 처리 성공"))
                .andExpect(jsonPath("$.data.action").value("BAN"))
                .andDo(print());
    }

    @Test
    @DisplayName("신고 목록 조회 성공")
    void adminReportsSuccess() throws Exception {
        given(reportService.getAllReports()).willReturn(List.of(Map.of("id", "report-1")));

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("신고 내역 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("report-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("신고 상태 변경 성공")
    void updateReportStatusSuccess() throws Exception {
        Map<String, Object> request = Map.of("status", "REVIEWING");
        given(reportService.updateReportStatus("report-1", "REVIEWING")).willReturn(Map.of("status", "REVIEWING"));

        mockMvc.perform(patch("/api/admin/reports/{reportId}", "report-1")
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
        mockMvc.perform(delete("/api/admin/posts/{postId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 강제 삭제 성공")
    void adminDeleteCommunityPostSuccess() throws Exception {
        mockMvc.perform(delete("/api/admin/community/posts/{postId}", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }
}
