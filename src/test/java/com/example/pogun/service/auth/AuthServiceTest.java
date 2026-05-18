package com.example.pogun.service.auth;

import com.example.pogun.config.firebase.FirebaseAuthProperties;
import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.OnboardingCompleteRequest;
import com.example.pogun.dto.location.RegionResponse;
import com.example.pogun.entity.user.PendingSocialSignup;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.PendingSocialSignupRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.example.pogun.service.location.KakaoLocalService;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private FirebaseAuthProperties firebaseAuthProperties;

    @Mock
    private FirebaseIdentityService firebaseIdentityService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private PendingSocialSignupRepository pendingSocialSignupRepository;

    @Mock
    private KakaoLocalService kakaoLocalService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    @Mock
    private NoticeChatService noticeChatService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginOrSignUp_createsPendingSignupInsteadOfUserForNewSocialIdentity() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "firebase-uid",
                "new-user@example.com",
                "New User",
                "https://cdn.example.com/profile.png",
                "google.com",
                List.of(
                        new FirebaseIdentityService.ProviderIdentity("google.com", "google-uid", "new-user@example.com"),
                        new FirebaseIdentityService.ProviderIdentity("email", "new-user@example.com", "new-user@example.com")
                ),
                java.util.Map.of()
        );
        when(firebaseIdentityService.verifyIdToken("id-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.save(any(PendingSocialSignup.class))).thenAnswer(invocation -> {
            PendingSocialSignup pending = invocation.getArgument(0);
            pending.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return pending;
        });

        AuthResponse response = authService.loginOrSignUp("id-token", httpServletRequest);

        assertThat(response.registrationStatus()).isEqualTo("PENDING_ONBOARDING");
        assertThat(response.id()).isNull();
        assertThat(response.pendingSignupId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(response.provider()).isEqualTo("GOOGLE");
        assertThat(response.linkedProviders()).containsExactly("GOOGLE");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginOrSignUp_createsPendingSignupForNewAppleIdentity() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "apple-firebase-uid",
                "new-user@privaterelay.appleid.com",
                "Apple User",
                null,
                "apple.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "new-user@privaterelay.appleid.com")),
                java.util.Map.of()
        );
        when(firebaseIdentityService.verifyIdToken("apple-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("apple-firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new-user@privaterelay.appleid.com")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("apple-firebase-uid")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.save(any(PendingSocialSignup.class))).thenAnswer(invocation -> {
            PendingSocialSignup pending = invocation.getArgument(0);
            pending.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            return pending;
        });

        AuthResponse response = authService.loginOrSignUp("apple-token", httpServletRequest);

        assertThat(response.registrationStatus()).isEqualTo("PENDING_ONBOARDING");
        assertThat(response.provider()).isEqualTo("APPLE");
        assertThat(response.linkedProviders()).containsExactly("APPLE");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginOrSignUp_usesAppleClaimNameWhenDisplayNameMatchesRelayAlias() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "apple-firebase-uid",
                "a1b2c3d4e5@privaterelay.appleid.com",
                "a1b2c3d4e5",
                null,
                "apple.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "a1b2c3d4e5@privaterelay.appleid.com")),
                java.util.Map.of(
                        "name", "준혁 장",
                        "given_name", "준혁",
                        "family_name", "장"
                )
        );
        when(firebaseIdentityService.verifyIdToken("apple-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("apple-firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("a1b2c3d4e5@privaterelay.appleid.com")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("apple-firebase-uid")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.save(any(PendingSocialSignup.class))).thenAnswer(invocation -> {
            PendingSocialSignup pending = invocation.getArgument(0);
            pending.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
            return pending;
        });

        AuthResponse response = authService.loginOrSignUp("apple-token", httpServletRequest);

        assertThat(response.registrationStatus()).isEqualTo("PENDING_ONBOARDING");
        assertThat(response.nickname()).isEqualTo("장준혁");
        assertThat(response.provider()).isEqualTo("APPLE");
    }

    @Test
    void loginOrSignUp_unlinksEmailProviderWhenSocialProviderExists() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "firebase-uid",
                "existing-user@example.com",
                "Existing User",
                "https://cdn.example.com/profile.png",
                "google.com",
                List.of(
                        new FirebaseIdentityService.ProviderIdentity("google.com", "google-uid", "existing-user@example.com"),
                        new FirebaseIdentityService.ProviderIdentity("email", "existing-user@example.com", "existing-user@example.com")
                ),
                java.util.Map.of()
        );
        User existing = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("existing-user@example.com")
                .nickname("기존 유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        UserSocialAccount emailAccount = UserSocialAccount.builder()
                .id(UUID.randomUUID())
                .user(existing)
                .provider("EMAIL")
                .providerEmail("existing-user@example.com")
                .linked(true)
                .build();

        when(firebaseIdentityService.verifyIdToken("id-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSocialAccountRepository.findByUserAndProvider(existing, "GOOGLE")).thenReturn(Optional.empty());
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(existing))
                .thenReturn(List.of(emailAccount))
                .thenReturn(List.<UserSocialAccount>of());

        AuthResponse response = authService.loginOrSignUp("id-token", httpServletRequest);

        assertThat(response.provider()).isEqualTo("GOOGLE");
        assertThat(response.linkedProviders()).containsExactly("GOOGLE");
        assertThat(emailAccount.getLinked()).isFalse();
        verify(userSocialAccountRepository).save(emailAccount);
        verify(userSocialAccountRepository, never()).findByUserAndProvider(existing, "EMAIL");
    }

    @Test
    void loginOrSignUp_keepsExistingHangulNicknameWhenIncomingIsFallback() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "apple-firebase-uid",
                "apple-user@privaterelay.appleid.com",
                "User_ab123",
                null,
                "apple.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "apple-user@privaterelay.appleid.com")),
                java.util.Map.of()
        );
        User existing = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("apple-firebase-uid")
                .email("apple-user@privaterelay.appleid.com")
                .nickname("장준혁")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(firebaseIdentityService.verifyIdToken("apple-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("apple-firebase-uid")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSocialAccountRepository.findByUserAndProvider(existing, "APPLE")).thenReturn(Optional.empty());
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(existing))
                .thenReturn(List.<UserSocialAccount>of())
                .thenReturn(List.<UserSocialAccount>of());

        AuthResponse response = authService.loginOrSignUp("apple-token", httpServletRequest);

        assertThat(response.nickname()).isEqualTo("장준혁");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("장준혁");
    }

    @Test
    void loginOrSignUp_keepsPendingNicknameWhenIncomingIsFallback() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "apple-pending-uid",
                "apple-pending@privaterelay.appleid.com",
                "User_knPnU",
                null,
                "apple.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "apple-pending@privaterelay.appleid.com")),
                java.util.Map.of()
        );
        PendingSocialSignup pending = PendingSocialSignup.builder()
                .id(UUID.randomUUID())
                .firebaseUid("apple-pending-uid")
                .email("apple-pending@privaterelay.appleid.com")
                .nickname("장준혁")
                .profileImageUrl(null)
                .provider("APPLE")
                .linkedProviders("APPLE")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(firebaseIdentityService.verifyIdToken("apple-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("apple-pending-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("apple-pending@privaterelay.appleid.com")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("apple-pending-uid")).thenReturn(Optional.of(pending));
        when(pendingSocialSignupRepository.save(any(PendingSocialSignup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.loginOrSignUp("apple-token", httpServletRequest);

        assertThat(response.registrationStatus()).isEqualTo("PENDING_ONBOARDING");
        assertThat(response.nickname()).isEqualTo("장준혁");
        ArgumentCaptor<PendingSocialSignup> pendingCaptor = ArgumentCaptor.forClass(PendingSocialSignup.class);
        verify(pendingSocialSignupRepository).save(pendingCaptor.capture());
        assertThat(pendingCaptor.getValue().getNickname()).isEqualTo("장준혁");
    }

    @Test
    void loginOrSignUp_usesFirebaseDisplayNameWhenAppleIdentityHasNoNameClaims() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "apple-no-name-uid",
                "myagmb28s@gmail.com",
                null,
                null,
                "apple.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "myagmb28s@gmail.com")),
                java.util.Map.of()
        );
        UserRecord firebaseUser = mock(UserRecord.class);
        when(firebaseUser.getDisplayName()).thenReturn("장준혁");

        when(firebaseIdentityService.verifyIdToken("apple-token")).thenReturn(identity);
        when(firebaseAuth.getUser("apple-no-name-uid")).thenReturn(firebaseUser);
        when(userRepository.findByFirebaseUid("apple-no-name-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("myagmb28s@gmail.com")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("apple-no-name-uid")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.save(any(PendingSocialSignup.class))).thenAnswer(invocation -> {
            PendingSocialSignup pending = invocation.getArgument(0);
            pending.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
            return pending;
        });

        AuthResponse response = authService.loginOrSignUp("apple-token", httpServletRequest);

        assertThat(response.registrationStatus()).isEqualTo("PENDING_ONBOARDING");
        assertThat(response.nickname()).isEqualTo("장준혁");
    }

    @Test
    void completeOnboarding_createsActiveUserFromPendingSignup() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid", "id-token")
        );
        OnboardingCompleteRequest request = new OnboardingCompleteRequest();
        request.setX(127.1086228);
        request.setY(37.4012191);

        PendingSocialSignup pending = PendingSocialSignup.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .firebaseUid("firebase-uid")
                .email("new-user@example.com")
                .nickname("New User")
                .profileImageUrl("https://cdn.example.com/profile.png")
                .provider("GOOGLE")
                .linkedProviders("GOOGLE")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        when(pendingSocialSignupRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(pending));
        when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());
        when(kakaoLocalService.resolveRegion(127.1086228, 37.4012191)).thenReturn(new RegionResponse(
                "H",
                "경기도 성남시 분당구 삼평동",
                "경기도",
                "성남시 분당구",
                "삼평동"
        ));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
            return user;
        });
        when(userSocialAccountRepository.findByUserAndProvider(any(User.class), any(String.class))).thenReturn(Optional.empty());

        AuthResponse response = authService.completeOnboarding(request);

        assertThat(response.registrationStatus()).isEqualTo("COMPLETED");
        assertThat(response.id()).isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(response.pendingSignupId()).isNull();
        assertThat(response.region()).isEqualTo("경기도 성남시 분당구 삼평동");
        assertThat(response.regionInfo().regionType()).isEqualTo("H");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userCaptor.getValue().getRegion()).isEqualTo("경기도 성남시 분당구 삼평동");
        assertThat(userCaptor.getValue().getRegion2DepthName()).isEqualTo("성남시 분당구");
        verify(userPresenceService).touchFromAuthenticationSafely("firebase-uid", null);
        verify(pendingSocialSignupRepository).deleteByEmailIgnoreCase("new-user@example.com");
    }

    @Test
    void logoutForcesUserOfflineAndPublishesPresence() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("user@example.com")
                .availabilityStatus(UserAvailabilityStatus.ONLINE)
                .status(UserStatus.ACTIVE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid", "id-token")
        );

        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(firebaseAuthProperties.isEmulatorMode()).thenReturn(false);
        when(noticeChatServiceProvider.getObject()).thenReturn(noticeChatService);

        LogoutResponse response = authService.logout();

        assertThat(response.revoked()).isTrue();
        verify(firebaseAuth).revokeRefreshTokens("firebase-uid");
        verify(firebaseIdentityService).revokeTokenLocally("id-token");
        verify(userPresenceService).forceOffline("firebase-uid");
        verify(noticeChatService).publishPresenceUpdates(user);
    }

    @Test
    void loginOrSignUp_keepsExistingManualAvailabilityStatus() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "firebase-uid",
                "existing-user@example.com",
                "Existing User",
                "https://cdn.example.com/profile.png",
                "google.com",
                List.of(new FirebaseIdentityService.ProviderIdentity("google.com", "google-uid", "existing-user@example.com")),
                java.util.Map.of()
        );
        User existing = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("existing-user@example.com")
                .nickname("기존 유저")
                .availabilityStatus(UserAvailabilityStatus.IDLE)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(firebaseIdentityService.verifyIdToken("id-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(any(User.class))).thenReturn(List.of());
        when(userSocialAccountRepository.findByUserAndProvider(any(User.class), any(String.class))).thenReturn(Optional.empty());

        authService.loginOrSignUp("id-token", httpServletRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailabilityStatus()).isEqualTo(UserAvailabilityStatus.IDLE);
    }

    @Test
    void loginAdmin_allowsExistingAdminUserWithFirebaseProvider() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "admin-uid",
                "admin@example.com",
                "관리자",
                null,
                "password",
                List.of(),
                java.util.Map.of()
        );
        User admin = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("admin-uid")
                .email("admin@example.com")
                .nickname("기존 관리자")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        when(firebaseIdentityService.verifyIdToken("admin-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("admin-uid")).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(any(User.class))).thenReturn(List.of());

        AuthResponse response = authService.loginAdmin("admin-token", httpServletRequest);

        assertThat(response.id()).isEqualTo(admin.getId());
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.registrationStatus()).isEqualTo("COMPLETED");
        assertThat(response.provider()).isEqualTo("FIREBASE");
        verify(userPresenceService).touchFromAuthenticationSafely("admin-uid", "unknown-host");
    }

    @Test
    void loginAdmin_rejectsNonAdminUser() throws Exception {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "user-uid",
                "user@example.com",
                "사용자",
                null,
                "password",
                List.of(),
                java.util.Map.of()
        );
        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("user-uid")
                .email("user@example.com")
                .nickname("일반 사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(firebaseIdentityService.verifyIdToken("user-token")).thenReturn(identity);
        when(userRepository.findByFirebaseUid("user-uid")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginAdmin("user-token", httpServletRequest))
                .hasMessageContaining("관리자 계정만 로그인할 수 있습니다.");
    }
}
