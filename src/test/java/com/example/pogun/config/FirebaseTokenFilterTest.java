package com.example.pogun.config;

import com.example.pogun.repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FirebaseTokenFilterTest {

    @Test
    @DisplayName("유효하지 않은 Firebase 토큰이면 JSON 401 응답을 반환한다")
    void invalidTokenReturnsJsonUnauthorized() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UserRepository userRepository = mock(UserRepository.class);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ApiErrorResponseWriter errorResponseWriter = new ApiErrorResponseWriter(objectMapper);
        FirebaseTokenFilter filter = new FirebaseTokenFilter(firebaseAuth, userRepository, errorResponseWriter);

        given(firebaseAuth.verifyIdToken(anyString(), anyBoolean()))
                .willThrow(new RuntimeException("invalid token"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
        verifyNoInteractions(userRepository);
    }
}