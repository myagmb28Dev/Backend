package com.example.pogun.controller.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("필수 multipart 파트가 없으면 400 MISSING_PART를 반환한다")
    void missingRequestPartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/test/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error.code").value("MISSING_PART"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type이면 415 UNSUPPORTED_MEDIA_TYPE를 반환한다")
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain-text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드면 405 METHOD_NOT_ALLOWED를 반환한다")
    void methodNotAllowedReturns405() throws Exception {
        mockMvc.perform(get("/test/json"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/upload")
        void upload(@RequestPart("file") MultipartFile file) {
        }

        @PostMapping(value = "/test/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void json(@RequestBody TestBody body) {
        }
    }

    static class TestBody {
        private final String value;

        @JsonCreator
        TestBody(@JsonProperty("value") String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}