package com.example.pogun.controller.auth;

import com.example.pogun.config.web.KakaoLocalProperties;
import com.example.pogun.controller.common.GlobalExceptionHandler;
import com.example.pogun.service.auth.AuthService;
import com.example.pogun.service.auth.FirebaseTokenRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private FirebaseTokenRefreshService firebaseTokenRefreshService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(
                                authService,
                                firebaseTokenRefreshService,
                                new KakaoLocalProperties()
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminLoginEndpoint_isDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firebaseIdToken": "admin-token"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEGACY_ADMIN_LOGIN_DISABLED"));

        verifyNoInteractions(authService);
    }
}
