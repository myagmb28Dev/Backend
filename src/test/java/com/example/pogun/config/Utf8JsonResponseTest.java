package com.example.pogun.config;

import com.example.pogun.controller.common.GlobalExceptionHandler;
import com.example.pogun.config.startup.StartupWarmupState;
import com.example.pogun.config.web.WebMvcConfig;
import com.example.pogun.controller.common.HealthController;
import com.example.pogun.dto.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Utf8JsonResponseTest {

    private static final MediaType APPLICATION_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StartupWarmupState startupWarmupState = new StartupWarmupState();
        startupWarmupState.markReady();

        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(startupWarmupState), new TestApiController())
                .addFilters(new WebMvcConfig.Utf8JsonContentTypeFilter())
                .setControllerAdvice(new GlobalExceptionHandler(), new WebMvcConfig.Utf8JsonResponseAdvice())
                .setMessageConverters(jacksonConverter())
                .build();
    }

    @Test
    void successResponseUsesUtf8JsonContentType() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.message").value("서비스가 정상 동작 중입니다."));
    }

    @Test
    void controllerExceptionResponseUsesUtf8JsonContentType() throws Exception {
        mockMvc.perform(get("/test/api-error"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.error.code").value("TEST_ERROR"));
    }

    @Test
    void missingParameterResponseUsesUtf8JsonContentType() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"));
    }

    private HttpMessageConverter<?> jacksonConverter() {
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(List.of(WebMvcConfig.APPLICATION_JSON_UTF8));
        return converter;
    }

    @RestController
    static class TestApiController {

        @GetMapping("/test/api-error")
        ApiResponse<Void> error() {
            throw ApiResponse.ApiException.badRequest("TEST_ERROR", "테스트 오류");
        }

        @GetMapping("/test/missing-param")
        ApiResponse<String> missing(@RequestParam("value") String value) {
            return ApiResponse.success(HttpStatus.OK, "unused", value);
        }
    }
}
