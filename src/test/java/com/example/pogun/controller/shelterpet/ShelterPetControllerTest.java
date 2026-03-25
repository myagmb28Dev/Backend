package com.example.pogun.controller.shelterpet;

import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetImageAnalysisDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetImageAnalysisResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListFiltersResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusUpdateRequest;
import com.example.pogun.dto.shelterpet.ShelterPetSummaryResponse;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateRequest;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateResponse;
import com.example.pogun.dto.shelterpet.ShelterPetViewResponse;
import com.example.pogun.service.shelterpet.ShelterPetService;
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

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShelterPetControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ShelterPetService shelterPetService;

    @InjectMocks
    private ShelterPetController shelterPetController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(shelterPetController).build();
    }

    @Test
    @DisplayName("외부 공고 목록 조회 성공")
    void listSuccess() throws Exception {
        given(shelterPetService.getShelterPetList("서울", "푸들", "NOTICE", "LATEST", 0, 20))
                .willReturn(new ShelterPetListResponse(
                        "KOREA_ANIMAL_PROTECTION_API",
                        new ShelterPetListFiltersResponse("서울", "푸들", "NOTICE", "LATEST", 0, 20),
                        List.of(new ShelterPetSummaryResponse("shelter-1", "서울", "푸들", "NOTICE"))
                ));

        mockMvc.perform(get("/api/shelter")
                        .param("region", "서울")
                        .param("breed", "푸들")
                        .param("status", "NOTICE")
                        .param("sort", "LATEST")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("전국 유기 동물 공고 조회 성공"))
                .andExpect(jsonPath("$.data.items[0].id").value("shelter-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("외부 공고 상세 조회 성공")
    void detailSuccess() throws Exception {
        given(shelterPetService.getShelterPetDetail("shelter-1")).willReturn(new ShelterPetDetailResponse(
                "shelter-1",
                "말티즈 실종",
                "흰색 목줄 착용",
                300000,
                "010-1111-2222",
                List.of("https://cdn.ex.com/n1-1.jpg")
        ));

        mockMvc.perform(get("/api/shelter/{id}", "shelter-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("유기 동물 공고 상세 조회 성공"))
                .andExpect(jsonPath("$.data.id").value("shelter-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("외부 공고 수정 성공")
    void updateSuccess() throws Exception {
        ShelterPetUpdateRequest request = new ShelterPetUpdateRequest();
        request.setTitle("수정");
        given(shelterPetService.updateShelterPet(eq("shelter-1"), anyMap())).willReturn(new ShelterPetUpdateResponse(
                "shelter-1",
                true,
                "수정",
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(patch("/api/shelter/{id}", "shelter-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("외부 공고 수정 성공"))
                .andExpect(jsonPath("$.data.id").value("shelter-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("외부 공고 상태 변경 성공")
    void changeStatusSuccess() throws Exception {
        ShelterPetStatusUpdateRequest request = new ShelterPetStatusUpdateRequest();
        request.setStatus("PROTECTED");
        given(shelterPetService.changeShelterPetStatus("shelter-1", "PROTECTED")).willReturn(new ShelterPetStatusResponse("shelter-1", "PROTECTED"));

        mockMvc.perform(patch("/api/shelter/{id}/status", "shelter-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("외부 공고 상태 변경 성공"))
                .andExpect(jsonPath("$.data.status").value("PROTECTED"))
                .andDo(print());
    }

    @Test
    @DisplayName("외부 공고 조회수 증가 성공")
    void increaseViewSuccess() throws Exception {
        given(shelterPetService.increaseShelterPetView("shelter-1")).willReturn(new ShelterPetViewResponse("shelter-1", 11));

        mockMvc.perform(post("/api/shelter/{id}/views", "shelter-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회수 증가 처리 완료"))
                .andExpect(jsonPath("$.data.viewCount").value(11))
                .andDo(print());
    }

    @Test
    @DisplayName("외부 공고 이미지 분석 성공")
    void analyzeImageSuccess() throws Exception {
        given(shelterPetService.analyzeShelterPetImage("shelter-1")).willReturn(new ShelterPetImageAnalysisResponse(
                new ShelterPetImageAnalysisDetailResponse("shelter-1", "SUCCESS", List.of("흰색"), List.of("notice-1"))
        ));

        mockMvc.perform(post("/api/shelter/{id}/analyze-image", "shelter-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("동물 사진 특징 분석 완료"))
                .andExpect(jsonPath("$.data.analysis.analysisStatus").value("SUCCESS"))
                .andDo(print());
    }
}
