package com.example.pogun.controller.auth;

import com.example.pogun.controller.common.GlobalExceptionHandler;
import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LoginRequest;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.SocialUnlinkResponse;
import com.example.pogun.dto.auth.WithdrawResponse;
import com.example.pogun.service.auth.AuthService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

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
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("로그인 성공 테스트 - 올바른 토큰 전달 시")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setFirebaseIdToken("valid-token");

        AuthResponse mockData = new AuthResponse(
                UUID.randomUUID(),
                "firebase-uid-1",
                "test@example.com",
                "포근",
                "",
                "GOOGLE",
                List.of("GOOGLE"),
                "USER"
        );
        given(authService.loginOrSignUp("valid-token")).willReturn(mockData);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
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
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andDo(print());
    }

    @Test
    @DisplayName("로그아웃 성공 테스트")
    void logoutSuccess() throws Exception {
        given(authService.logout()).willReturn(new LogoutResponse(true, "로그아웃 성공. 모든 세션이 만료되었습니다."));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.message").value("로그아웃 처리 완료"))
                .andDo(print());
    }

    @Test
    @DisplayName("회원 탈퇴 성공 테스트")
    void withdrawSuccess() throws Exception {
        given(authService.withdraw()).willReturn(new WithdrawResponse("WITHDRAWN", "회원 탈퇴 및 Firebase 계정 삭제가 완료되었습니다."));

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
        given(authService.unlinkSocial(provider)).willReturn(new SocialUnlinkResponse(provider, true, List.of("APPLE"), "GOOGLE 계정 연결이 해제되었습니다."));

        mockMvc.perform(delete("/api/auth/link/{provider}", provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.message").value("소셜 계정 연동 해제 성공"))
                .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
                .andDo(print());
    }
}
