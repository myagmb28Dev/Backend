package com.example.pogun.config.security;

import com.example.pogun.config.web.WebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseWriterTest {

    @Test
    void writeUsesUtf8JsonContentType() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "인증이 필요합니다.", null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).isEqualTo(WebMvcConfig.APPLICATION_JSON_UTF8.toString());
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("인증이 필요합니다.");
    }
}
