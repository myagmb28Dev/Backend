package com.example.demo.controller;

import com.example.demo.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AiService aiService;

    @InjectMocks
    private AiController aiController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();
    }

    @Test
    @DisplayName("사진 업로드 성공")
    void uploadPhotoSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "dog.jpg", MediaType.IMAGE_JPEG_VALUE, "image".getBytes());
        given(aiService.uploadPhoto(any())).willReturn(Map.of("photoId", "photo-1"));

        mockMvc.perform(multipart("/api/ai/photos").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("분석용 사진 업로드 성공"))
                .andExpect(jsonPath("$.data.photoId").value("photo-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("사진 업로드 실패 - 빈 파일")
    void uploadPhotoFailEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/ai/photos").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andDo(print());
    }

    @Test
    @DisplayName("사진 분석 요청 성공")
    void requestAnalysisSuccess() throws Exception {
        Map<String, Object> request = Map.of("photoId", "photo-1");
        given(aiService.requestAnalysis(request)).willReturn(Map.of("analysisId", "analysis-1"));

        mockMvc.perform(post("/api/ai/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("사진 분석 요청 성공"))
                .andExpect(jsonPath("$.data.analysisId").value("analysis-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("분석 결과 조회 성공")
    void getAnalysisSuccess() throws Exception {
        given(aiService.getAnalysisResult("analysis-1")).willReturn(Map.of("analysisId", "analysis-1"));

        mockMvc.perform(get("/api/ai/analysis/{analysisId}", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분석 결과 조회 성공"))
                .andExpect(jsonPath("$.data.analysisId").value("analysis-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("유사 공고 추천 조회 성공")
    void getSimilarPostsSuccess() throws Exception {
        given(aiService.getSimilarPosts("analysis-1")).willReturn(Map.of("items", java.util.List.of(Map.of("id", "notice-1"))));

        mockMvc.perform(get("/api/ai/analysis/{analysisId}/similar-posts", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("유사 공고 추천 조회 성공"))
                .andExpect(jsonPath("$.data.items[0].id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("분석 재시도 성공")
    void retryAnalysisSuccess() throws Exception {
        given(aiService.retryAnalysis("analysis-1")).willReturn(Map.of("retried", true));

        mockMvc.perform(post("/api/ai/analysis/{analysisId}/retry", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분석 재시도 요청 성공"))
                .andExpect(jsonPath("$.data.retried").value(true))
                .andDo(print());
    }
}
