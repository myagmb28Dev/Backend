package com.example.pogun.service.adminauth;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.dto.admin.auth.AdminLoginResponse;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.admin.AdminAuthChallengeRepository;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.repository.admin.AdminSessionRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.auth.FirebaseIdentityService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private FirebaseAuth firebaseAuth;
    @Mock
    private FirebaseIdentityService firebaseIdentityService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminPasskeyRepository adminPasskeyRepository;
    @Mock
    private AdminAuthChallengeRepository adminAuthChallengeRepository;
    @Mock
    private AdminSessionRepository adminSessionRepository;
    @Mock
    private AdminSessionTokenService adminSessionTokenService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminEmailVerificationService adminEmailVerificationService;
    @Mock
    private AdminSecurityService adminSecurityService;
    @Mock
    private AdminWebAuthnService adminWebAuthnService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminConsoleProperties adminConsoleProperties;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Test
    void loginWithGoogleToken_requiresEmailVerificationForRoleOnlyAdmin() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("admin@example.com")
                .nickname("admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .adminEmailVerificationRequired(false)
                .adminEmailVerifiedAt(null)
                .build();
        UserRecord firebaseUser = org.mockito.Mockito.mock(UserRecord.class);

        when(firebaseIdentityService.verifyIdToken("id-token", true))
                .thenReturn(new FirebaseIdentityService.FirebaseIdentity(
                        "firebase-uid",
                        "admin@example.com",
                        "Admin",
                        null,
                        "google.com",
                        List.of(new FirebaseIdentityService.ProviderIdentity("google.com", "google-uid", "admin@example.com")),
                        Map.of()
                ));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(firebaseAuth.getUser("firebase-uid")).thenReturn(firebaseUser);
        when(firebaseUser.isEmailVerified()).thenReturn(false);

        AdminLoginResponse response = adminAuthService.loginWithGoogleToken("id-token", httpServletRequest);

        assertThat(response.nextStep()).isEqualTo("EMAIL_VERIFICATION_REQUIRED");
        assertThat(response.requiresPassKey()).isFalse();
        assertThat(admin.isAdminEmailVerificationRequired()).isTrue();
        verify(adminEmailVerificationService).sendVerificationEmail("id-token", httpServletRequest);
        verify(adminPasskeyRepository, never()).existsByUser(any(User.class));
    }
}

