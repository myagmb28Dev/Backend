package com.example.pogun.config;

import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.admin.enums.AdminSessionStage;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.adminauth.AdminPermissionService;
import com.example.pogun.service.adminauth.AdminSessionTokenService;
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
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSessionAuthenticationFilterTest {

    @Mock
    private AdminSessionTokenService adminSessionTokenService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminPasskeyRepository adminPasskeyRepository;
    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_revokesAuthenticatedSessionWhenPasskeyRemoved() throws ServletException, IOException {
        User admin = adminUser();
        AdminSession session = adminSession(admin, AdminSessionStage.AUTHENTICATED);

        when(adminSessionTokenService.resolve("admin-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(adminPasskeyRepository.existsByUser(admin)).thenReturn(false);

        AdminSessionAuthenticationFilter filter = new AdminSessionAuthenticationFilter(
                adminSessionTokenService,
                adminPermissionService,
                adminPasskeyRepository,
                userRepository
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/reports");
        request.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        verify(adminSessionTokenService).revoke(session);
        verify(adminPermissionService, never()).getPermissions(admin);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_keepsAuthenticatedSessionWhenPasskeyExists() throws ServletException, IOException {
        User admin = adminUser();
        AdminSession session = adminSession(admin, AdminSessionStage.AUTHENTICATED);

        when(adminSessionTokenService.resolve("admin-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(adminPasskeyRepository.existsByUser(admin)).thenReturn(true);
        when(adminPermissionService.getPermissions(admin)).thenReturn(Set.of(AdminPermission.REPORT_REVIEW));

        AdminSessionAuthenticationFilter filter = new AdminSessionAuthenticationFilter(
                adminSessionTokenService,
                adminPermissionService,
                adminPasskeyRepository,
                userRepository
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/reports");
        request.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        verify(adminSessionTokenService, never()).revoke(session);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_ADMIN_CONSOLE", "ADMIN_PERMISSION_REPORT_REVIEW");
    }

    private User adminUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("admin-firebase-uid")
                .email("admin@test.dev")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private AdminSession adminSession(User user, AdminSessionStage stage) {
        return AdminSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("hash")
                .stage(stage)
                .expiresAt(Instant.now().plusSeconds(600))
                .lastUsedAt(Instant.now())
                .build();
    }
}

