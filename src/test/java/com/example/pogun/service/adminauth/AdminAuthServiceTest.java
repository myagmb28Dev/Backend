package com.example.pogun.service.adminauth;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.dto.admin.auth.AdminLoginResponse;
import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.admin.enums.AdminSessionStage;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                .profileImageUrl("https://example.com/admin.png")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .adminEmailVerificationRequired(false)
                .adminEmailVerifiedAt(null)
                .build();

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

        AdminLoginResponse response = adminAuthService.loginWithGoogleToken("id-token", httpServletRequest);

        assertThat(response.nextStep()).isEqualTo("EMAIL_VERIFICATION_REQUIRED");
        assertThat(response.requiresPassKey()).isFalse();
        assertThat(admin.isAdminEmailVerificationRequired()).isTrue();
        assertThat(admin.getAdminEmailVerificationSentAt()).isNotNull();
        verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
        verify(adminEmailVerificationService).sendVerificationEmail("id-token", httpServletRequest);
        verify(adminPasskeyRepository, never()).existsByUser(any(User.class));
    }

    @Test
    void loginWithGoogleToken_doesNotSkipFirstVerificationMailWhenFirebaseAlreadyVerified() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("admin@example.com")
                .nickname("admin")
                .profileImageUrl("https://example.com/admin.png")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .adminEmailVerificationRequired(true)
                .adminEmailVerifiedAt(null)
                .adminEmailVerificationSentAt(null)
                .build();

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

        AdminLoginResponse response = adminAuthService.loginWithGoogleToken("id-token", httpServletRequest);

        assertThat(response.nextStep()).isEqualTo("EMAIL_VERIFICATION_REQUIRED");
        assertThat(response.requiresPassKey()).isFalse();
        assertThat(admin.getAdminEmailVerifiedAt()).isNull();
        assertThat(admin.getAdminEmailVerificationSentAt()).isNotNull();
        verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
        verify(adminEmailVerificationService).sendVerificationEmail("id-token", httpServletRequest);
        verify(adminPasskeyRepository, never()).existsByUser(any(User.class));
    }

    @Test
    void loginWithGoogleToken_allowsPasskeyStageOnlyAfterSentMailAndFirebaseVerified() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("admin@example.com")
                .nickname("admin")
                .profileImageUrl("https://example.com/admin.png")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .adminEmailVerificationRequired(true)
                .adminEmailVerifiedAt(null)
                .adminEmailVerificationSentAt(Instant.now())
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
        when(firebaseUser.isEmailVerified()).thenReturn(true);
        when(adminPermissionService.getPermissions(admin)).thenReturn(Set.of(AdminPermission.ADMIN_PROMOTE));
        when(adminPasskeyRepository.findByUserOrderByCreatedAtAsc(admin)).thenReturn(List.of());
        when(adminConsoleProperties.getBootstrapSessionTtlSeconds()).thenReturn(900L);
        AdminSession session = AdminSession.builder()
                .id(UUID.randomUUID())
                .user(admin)
                .stage(AdminSessionStage.PASSKEY_ENROLL)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(adminSessionTokenService.issue(admin, AdminSessionStage.PASSKEY_ENROLL, 900L))
                .thenReturn(new AdminSessionTokenService.IssuedSession("session-token", session));

        AdminLoginResponse response = adminAuthService.loginWithGoogleToken("id-token", httpServletRequest);

        assertThat(admin.isAdminEmailVerificationRequired()).isFalse();
        assertThat(admin.getAdminEmailVerifiedAt()).isNotNull();
        assertThat(admin.getAdminEmailVerificationSentAt()).isNull();
        assertThat(response.requiresPassKey()).isTrue();
        assertThat(response.nextStep()).isEqualTo("PASSKEY_REGISTRATION_REQUIRED");
        verify(adminEmailVerificationService, never()).sendVerificationEmail(any(), any());
    }
}

