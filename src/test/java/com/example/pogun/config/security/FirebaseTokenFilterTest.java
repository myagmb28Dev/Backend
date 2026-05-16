package com.example.pogun.config.security;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.auth.FirebaseIdentityService;
import com.example.pogun.service.user.UserPresenceService;
import com.google.firebase.auth.FirebaseAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseTokenFilterTest {

    @Mock
    private FirebaseIdentityService firebaseIdentityService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPresenceService userPresenceService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_rejectsBannedUserOnProtectedApi() throws ServletException, IOException, FirebaseAuthException {
        FirebaseTokenFilter filter = new FirebaseTokenFilter(
                firebaseIdentityService,
                userRepository,
                userPresenceService,
                new ApiErrorResponseWriter(new ObjectMapper().findAndRegisterModules())
        );
        User bannedUser = user(UserStatus.BANNED);
        when(firebaseIdentityService.verifyIdToken("id-token", true))
                .thenReturn(new FirebaseIdentityService.FirebaseIdentity("firebase-uid", "user@test.dev", "User", null, "password", List.of(), Map.of()));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(bannedUser));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing-pets");
        request.addHeader("Authorization", "Bearer id-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("USER_BANNED");
        verify(userPresenceService, never()).touch("firebase-uid");
        verify(userPresenceService, never()).touchFromAuthenticationSafely(anyString(), anyString());
    }

    @Test
    void doFilterInternal_allowsActiveAdminOnNonAdminApiAndSetsRole() throws ServletException, IOException, FirebaseAuthException {
        FirebaseTokenFilter filter = new FirebaseTokenFilter(
                firebaseIdentityService,
                userRepository,
                userPresenceService,
                new ApiErrorResponseWriter(new ObjectMapper().findAndRegisterModules())
        );
        User admin = user(UserStatus.ACTIVE);
        admin.setRole(UserRole.ADMIN);
        when(firebaseIdentityService.verifyIdToken("id-token", true))
                .thenReturn(new FirebaseIdentityService.FirebaseIdentity("firebase-uid", "admin@test.dev", "Admin", null, "password", List.of(), Map.of()));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(admin));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing-pets");
        request.addHeader("Authorization", "Bearer id-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
        verify(userPresenceService).touchFromAuthenticationSafely("firebase-uid", "localhost");
    }

    private User user(UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("user@test.dev")
                .nickname("사용자")
                .role(UserRole.USER)
                .status(status)
                .build();
    }
}
