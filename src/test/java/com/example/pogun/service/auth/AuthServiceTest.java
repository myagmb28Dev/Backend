package com.example.pogun.service.auth;

import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        lenient().when(userSocialAccountRepository.save(any(UserSocialAccount.class))).thenAnswer(invocation -> {
            UserSocialAccount account = invocation.getArgument(0);
            if (account.getId() == null) {
                account.setId(UUID.randomUUID());
            }
            return account;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Firebase 로그인 시 기존 사용자를 최신 정보로 갱신한다")
    void loginOrSignUp_withExistingFirebaseUser_updatesUser() throws Exception {
        FirebaseToken decodedToken = org.mockito.Mockito.mock(FirebaseToken.class);
        UserRecord userRecord = org.mockito.Mockito.mock(UserRecord.class);
        UserInfo providerInfo = org.mockito.Mockito.mock(UserInfo.class);
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("old@example.com")
                .nickname("기존유저")
                .authProvider("EMAIL")
                .role(UserRole.USER)
                .status(UserStatus.WITHDRAWN)
                .build();

        given(firebaseAuth.verifyIdToken("firebase-token")).willReturn(decodedToken);
        given(decodedToken.getUid()).willReturn("firebase-uid-1");
        given(decodedToken.getEmail()).willReturn("updated@example.com");
        given(decodedToken.getClaims()).willReturn(Map.of("firebase", Map.of("sign_in_provider", "google.com")));
        given(firebaseAuth.getUser("firebase-uid-1")).willReturn(userRecord);
        given(userRecord.getDisplayName()).willReturn("업데이트유저");
        given(userRecord.getPhotoUrl()).willReturn("https://example.com/new-profile.png");
        given(userRecord.getProviderData()).willReturn(new UserInfo[]{providerInfo});
        given(providerInfo.getProviderId()).willReturn("google.com");
        given(providerInfo.getUid()).willReturn("google-provider-uid");
        given(providerInfo.getEmail()).willReturn("updated@example.com");

        given(userRepository.findByFirebaseUid("firebase-uid-1")).willReturn(Optional.of(existingUser));
        given(userSocialAccountRepository.findByUserAndProvider(existingUser, "GOOGLE")).willReturn(Optional.empty());
        given(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(any(User.class)))
                .willReturn(List.of(UserSocialAccount.builder().provider("GOOGLE").linked(true).build()));

        AuthResponse result = authService.loginOrSignUp("firebase-token");

        assertThat(existingUser.getEmail()).isEqualTo("updated@example.com");
        assertThat(existingUser.getNickname()).isEqualTo("업데이트유저");
        assertThat(existingUser.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(existingUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.firebaseUid()).isEqualTo("firebase-uid-1");
        assertThat(result.provider()).isEqualTo("GOOGLE");
        assertThat(result.linkedProviders()).isEqualTo(List.of("GOOGLE"));
        verify(firebaseAuth).verifyIdToken("firebase-token");
    }

    @Test
    @DisplayName("Firebase 로그인 시 신규 사용자를 생성하고 provider를 동기화한다")
    void loginOrSignUp_withFirebaseToken_createsUserAndSyncsProvider() throws Exception {
        FirebaseToken decodedToken = org.mockito.Mockito.mock(FirebaseToken.class);
        UserRecord userRecord = org.mockito.Mockito.mock(UserRecord.class);
        UserInfo providerInfo = org.mockito.Mockito.mock(UserInfo.class);

        given(firebaseAuth.verifyIdToken("firebase-token")).willReturn(decodedToken);
        given(decodedToken.getUid()).willReturn("firebase-uid-1");
        given(decodedToken.getEmail()).willReturn("new@example.com");
        given(decodedToken.getClaims()).willReturn(Map.of("firebase", Map.of("sign_in_provider", "google.com")));
        given(firebaseAuth.getUser("firebase-uid-1")).willReturn(userRecord);
        given(userRecord.getDisplayName()).willReturn("새사용자");
        given(userRecord.getPhotoUrl()).willReturn("https://example.com/profile.png");
        given(userRecord.getProviderData()).willReturn(new UserInfo[]{providerInfo});
        given(providerInfo.getProviderId()).willReturn("google.com");
        given(providerInfo.getUid()).willReturn("google-provider-uid");
        given(providerInfo.getEmail()).willReturn("new@example.com");

        given(userRepository.findByFirebaseUid("firebase-uid-1")).willReturn(Optional.empty());
        given(userSocialAccountRepository.findByUserAndProvider(any(User.class), anyString())).willReturn(Optional.empty());
        given(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(any(User.class)))
                .willReturn(List.of(UserSocialAccount.builder().provider("GOOGLE").linked(true).build()));

        AuthResponse result = authService.loginOrSignUp("firebase-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getFirebaseUid()).isEqualTo("firebase-uid-1");
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getNickname()).isEqualTo("새사용자");
        assertThat(savedUser.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.provider()).isEqualTo("GOOGLE");
        verify(userSocialAccountRepository).save(any(UserSocialAccount.class));
    }

    @Test
    @DisplayName("잘못된 Firebase 토큰이면 로그인에 실패한다")
    void loginOrSignUp_withInvalidToken_throws() throws Exception {
        FirebaseAuthException exception = org.mockito.Mockito.mock(FirebaseAuthException.class);
        given(firebaseAuth.verifyIdToken("invalid-token")).willThrow(exception);

        assertThatThrownBy(() -> authService.loginOrSignUp("invalid-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("인증 오류가 발생했습니다");
    }

    @Test
    @DisplayName("일반 사용자 로그아웃 시 Firebase refresh token을 revoke 한다")
    void logout_forNormalUser_revokesRefreshToken() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("user@example.com")
                .nickname("닉네임")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid-1", null)
        );
        given(userRepository.findByFirebaseUid("firebase-uid-1")).willReturn(Optional.of(user));

        LogoutResponse result = authService.logout();

        assertThat(result.revoked()).isTrue();
        verify(firebaseAuth).revokeRefreshTokens("firebase-uid-1");
    }

    @Test
    @DisplayName("마지막 소셜 계정은 해제할 수 없다")
    void unlinkSocial_whenOnlyOneLinkedAccount_throws() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid-1")
                .email("user@example.com")
                .nickname("닉네임")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        UserSocialAccount googleAccount = UserSocialAccount.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider("GOOGLE")
                .linked(true)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("firebase-uid-1", null)
        );
        given(userRepository.findByFirebaseUid("firebase-uid-1")).willReturn(Optional.of(user));
        given(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user)).willReturn(List.of(googleAccount));
        given(userSocialAccountRepository.findByUserAndProvider(user, "GOOGLE")).willReturn(Optional.of(googleAccount));

        assertThatThrownBy(() -> authService.unlinkSocial("GOOGLE"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("마지막 소셜 계정은 해제할 수 없습니다");

        verify(userSocialAccountRepository, never()).save(any(UserSocialAccount.class));
        verify(firebaseAuth, never()).revokeRefreshTokens(anyString());
    }
}

