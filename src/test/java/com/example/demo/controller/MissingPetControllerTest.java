package com.example.demo.controller;

import com.example.demo.service.MissingPetService;
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

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MissingPetControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private MissingPetService missingPetService;

    @InjectMocks
    private MissingPetController missingPetController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(missingPetController).build();
    }

    @Test
    @DisplayName("실종 공고 목록 조회 성공")
    void listSuccess() throws Exception {
        given(missingPetService.getMissingPetList("서울", "말티즈", "OPEN", "2026-03-01", "2026-03-23", "latest", 0, 20))
                .willReturn(Map.of("items", java.util.List.of(Map.of("id", "notice-1"))));

        mockMvc.perform(get("/api/missing-pets")
                        .param("region", "서울")
                        .param("breed", "말티즈")
                        .param("status", "OPEN")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-23")
                        .param("sort", "latest")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 동물 공고 목록 조회 성공"))
                .andExpect(jsonPath("$.data.items[0].id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 생성 성공")
    void createSuccess() throws Exception {
        Map<String, Object> request = Map.of("title", "말티즈를 찾습니다");
        given(missingPetService.createMissingPet(request)).willReturn(Map.of("id", "notice-1"));

        mockMvc.perform(post("/api/missing-pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("행방불명 공고 생성 성공"))
                .andExpect(jsonPath("$.data.id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 상세 조회 성공")
    void detailSuccess() throws Exception {
        given(missingPetService.getMissingPetDetail("notice-1")).willReturn(Map.of("id", "notice-1"));

        mockMvc.perform(get("/api/missing-pets/{missingPetId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 상세 조회 성공"))
                .andExpect(jsonPath("$.data.id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 수정 성공")
    void updateSuccess() throws Exception {
        Map<String, Object> request = Map.of("title", "수정된 제목");
        given(missingPetService.updateMissingPet("notice-1", request)).willReturn(Map.of("id", "notice-1"));

        mockMvc.perform(patch("/api/missing-pets/{missingPetId}", "notice-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 수정 성공"))
                .andExpect(jsonPath("$.data.id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 상태 변경 성공")
    void changeStatusSuccess() throws Exception {
        Map<String, String> request = Map.of("status", "RESOLVED");
        given(missingPetService.changeMissingPetStatus("notice-1", "RESOLVED")).willReturn(Map.of("status", "RESOLVED"));

        mockMvc.perform(patch("/api/missing-pets/{missingPetId}/status", "notice-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 상태 변경 성공"))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 조회수 증가 성공")
    void increaseViewSuccess() throws Exception {
        given(missingPetService.increaseMissingPetView("notice-1")).willReturn(Map.of("viewCount", 10));

        mockMvc.perform(post("/api/missing-pets/{missingPetId}/view", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회수 증가 처리 완료"))
                .andExpect(jsonPath("$.data.viewCount").value(10))
                .andDo(print());
    }
}
