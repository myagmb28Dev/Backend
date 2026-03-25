package com.example.pogun.controller.missingpet;

import com.example.pogun.dto.missingpet.MissingPetCreateRequest;
import com.example.pogun.dto.missingpet.MissingPetDetailResponse;
import com.example.pogun.dto.missingpet.MissingPetListFiltersResponse;
import com.example.pogun.dto.missingpet.MissingPetListResponse;
import com.example.pogun.dto.missingpet.MissingPetStatusUpdateRequest;
import com.example.pogun.dto.missingpet.MissingPetSummaryResponse;
import com.example.pogun.dto.missingpet.MissingPetUpdateRequest;
import com.example.pogun.dto.missingpet.MissingPetViewResponse;
import com.example.pogun.service.missingpet.MissingPetService;
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
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
        given(missingPetService.getMissingPetList("서울", "말티즈", "OPEN", "2026-03-01", "2026-03-23", "latest", 0, 20))
                .willReturn(new MissingPetListResponse(
                        new MissingPetListFiltersResponse("서울", "말티즈", "OPEN", "2026-03-01", "2026-03-23", "latest", 0, 20),
                        1,
                        1,
                        List.of(new MissingPetSummaryResponse(
                                noticeId,
                                "말티즈를 찾습니다",
                                "DOG",
                                "말티즈",
                                Instant.parse("2026-03-18T10:00:00Z"),
                                "서울 강남구",
                                "OPEN",
                                "진행중",
                                false,
                                0L,
                                0L,
                                true,
                                "작성자",
                                List.of("https://example.com/a.jpg")
                        ))
                ));

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
                .andExpect(jsonPath("$.data.items[0].id").value(noticeId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 생성 성공")
    void createSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
        MissingPetCreateRequest request = new MissingPetCreateRequest();
        request.setTitle("말티즈를 찾습니다");
        request.setAnimalType("DOG");
        request.setMissingDate("2026-03-18T10:00:00Z");
        request.setMissingRegion("서울 강남구");
        given(missingPetService.createMissingPet(anyMap())).willReturn(buildDetailResponse(noticeId, "OPEN", 0L));

        mockMvc.perform(post("/api/missing-pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("행방불명 공고 생성 성공"))
                .andExpect(jsonPath("$.data.id").value(noticeId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 상세 조회 성공")
    void detailSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");
        given(missingPetService.getMissingPetDetail("notice-1")).willReturn(buildDetailResponse(noticeId, "OPEN", 1L));

        mockMvc.perform(get("/api/missing-pets/{missingPetId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 상세 조회 성공"))
                .andExpect(jsonPath("$.data.id").value(noticeId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 수정 성공")
    void updateSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");
        MissingPetUpdateRequest request = new MissingPetUpdateRequest();
        request.setTitle("수정된 제목");
        given(missingPetService.updateMissingPet(eq("notice-1"), anyMap())).willReturn(buildDetailResponse(noticeId, "OPEN", 1L));

        mockMvc.perform(patch("/api/missing-pets/{missingPetId}", "notice-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 수정 성공"))
                .andExpect(jsonPath("$.data.id").value(noticeId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("실종 공고 상태 변경 성공")
    void changeStatusSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440014");
        MissingPetStatusUpdateRequest request = new MissingPetStatusUpdateRequest();
        request.setStatus("RESOLVED");
        given(missingPetService.changeMissingPetStatus("notice-1", "RESOLVED")).willReturn(buildDetailResponse(noticeId, "RESOLVED", 1L));

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
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440015");
        given(missingPetService.increaseMissingPetView("notice-1")).willReturn(new MissingPetViewResponse(noticeId, 10L));

        mockMvc.perform(post("/api/missing-pets/{missingPetId}/view", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회수 증가 처리 완료"))
                .andExpect(jsonPath("$.data.viewCount").value(10))
                .andDo(print());
    }

    private MissingPetDetailResponse buildDetailResponse(UUID id, String status, long viewCount) {
        return new MissingPetDetailResponse(
                id,
                "말티즈를 찾습니다",
                "DOG",
                "말티즈",
                "MALE",
                3,
                "WHITE",
                "상세 설명",
                Instant.parse("2026-03-18T10:00:00Z"),
                "서울 강남구",
                "역삼역 인근",
                300000,
                "010-1111-2222",
                status,
                "RESOLVED".equals(status) ? "해결됨" : "진행중",
                !"OPEN".equals(status),
                viewCount,
                0L,
                true,
                false,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
                "작성자",
                Instant.parse("2026-03-18T11:00:00Z"),
                Instant.parse("2026-03-18T12:00:00Z"),
                List.of("https://example.com/a.jpg")
        );
    }
}