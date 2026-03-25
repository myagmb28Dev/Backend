package com.example.pogun.controller.ai;

import com.example.pogun.dto.ai.AiAnalysisCreateResponse;
import com.example.pogun.dto.ai.AiAnalysisRequest;
import com.example.pogun.dto.ai.AiAnalysisResultResponse;
import com.example.pogun.dto.ai.AiPhotoUploadResponse;
import com.example.pogun.dto.ai.AiRetryResponse;
import com.example.pogun.dto.ai.AiSimilarPostsResponse;
import com.example.pogun.service.ai.AiService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dog.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
        given(aiService.uploadPhoto(any())).willReturn(new AiPhotoUploadResponse("photo-1", "dog.jpg", 5L, MediaType.IMAGE_JPEG_VALUE));

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
    @DisplayName("사진 업로드 실패 - 허용되지 않는 contentType")
    void uploadPhotoFailInvalidContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "dog.jpg", MediaType.TEXT_PLAIN_VALUE, new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/ai/photos").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("허용되지 않는 파일 형식입니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("사진 업로드 실패 - 이미지 시그니처 불일치")
    void uploadPhotoFailInvalidSignature() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "dog.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/ai/photos").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("파일 내용이 유효한 이미지가 아닙니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("사진 분석 요청 성공")
    void requestAnalysisSuccess() throws Exception {
        AiAnalysisRequest request = new AiAnalysisRequest();
        request.setPhotoId("photo-1");
        given(aiService.requestAnalysis(anyMap())).willReturn(new AiAnalysisCreateResponse("analysis-1", "photo-1", "PENDING"));

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
        given(aiService.getAnalysisResult("analysis-1")).willReturn(new AiAnalysisResultResponse("analysis-1", "SUCCESS", List.of("흰색")));

        mockMvc.perform(get("/api/ai/analysis/{analysisId}", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분석 결과 조회 성공"))
                .andExpect(jsonPath("$.data.analysisId").value("analysis-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("유사 공고 추천 조회 성공")
    void getSimilarPostsSuccess() throws Exception {
        given(aiService.getSimilarPosts("analysis-1")).willReturn(new AiSimilarPostsResponse("analysis-1", List.of("notice-1")));

        mockMvc.perform(get("/api/ai/analysis/{analysisId}/similar-posts", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("유사 공고 추천 조회 성공"))
                .andExpect(jsonPath("$.data.items[0]").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("분석 재시도 성공")
    void retryAnalysisSuccess() throws Exception {
        given(aiService.retryAnalysis("analysis-1")).willReturn(new AiRetryResponse("analysis-1", "RETRYING"));

        mockMvc.perform(post("/api/ai/analysis/{analysisId}/retry", "analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분석 재시도 요청 성공"))
                .andExpect(jsonPath("$.data.status").value("RETRYING"))
                .andDo(print());
    }
}