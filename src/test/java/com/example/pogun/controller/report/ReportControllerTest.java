package com.example.pogun.controller.report;

import com.example.pogun.dto.report.ReportCreateRequest;
import com.example.pogun.dto.report.ReportResponse;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
    }

    @Test
    @DisplayName("신고 접수 성공")
    void reportSuccess() throws Exception {
        UUID reportId = UUID.fromString("550e8400-e29b-41d4-a716-446655440035");
        UUID targetId = UUID.fromString("550e8400-e29b-41d4-a716-446655440036");
        ReportCreateRequest request = new ReportCreateRequest();
        request.setTargetType("COMMUNITY_POST");
        request.setTargetId(targetId.toString());
        given(reportService.createReport(anyMap())).willReturn(new ReportResponse(
                reportId,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440037"),
                "COMMUNITY_POST",
                targetId,
                "SPAM",
                null,
                "RECEIVED",
                null,
                Instant.parse("2026-03-20T10:00:00Z")
        ));

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("신고 접수 성공"))
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andDo(print());
    }
}