package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.service.AuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("로그인 성공 테스트 - 올바른 토큰 전달 시")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setFirebaseIdToken("valid-token");

        Map<String, Object> mockData = Map.of("userId", 123, "email", "test@example.com");
        given(authService.loginOrSignUp("valid-token")).willReturn(mockData);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.userId").value(123))
                .andDo(print());
    }

    @Test
    @DisplayName("로그인 실패 테스트 - 토큰이 비어있을 때")
    void loginFailEmptyToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setFirebaseIdToken("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andDo(print());
    }

    @Test
    @DisplayName("로그아웃 성공 테스트")
    void logoutSuccess() throws Exception {
        given(authService.logout()).willReturn(Map.of("result", true));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.message").value("로그아웃 처리 완료"))
                .andDo(print());
    }

    @Test
    @DisplayName("회원 탈퇴 성공 테스트")
    void withdrawSuccess() throws Exception {
        given(authService.withdraw()).willReturn(Map.of("withdrawDate", "2026-03-23"));

        mockMvc.perform(delete("/api/auth/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴 처리 완료"))
                .andDo(print());
    }

    @Test
    @DisplayName("소셜 계정 연동 해제 테스트")
    void unlinkSocialSuccess() throws Exception {
        String provider = "GOOGLE";
        given(authService.unlinkSocial(provider)).willReturn(Map.of("unlinked", provider));

        mockMvc.perform(delete("/api/auth/link/{provider}", provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.message").value("소셜 계정 연동 해제 성공"))
                .andExpect(jsonPath("$.data.unlinked").value("GOOGLE"))
                .andDo(print());
    }
}
