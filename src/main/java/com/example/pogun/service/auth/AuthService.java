package com.example.pogun.service.auth;

import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.SocialUnlinkResponse;
import com.example.pogun.dto.auth.WithdrawResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.UserRole;
import com.example.pogun.entity.user.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * 도메인 비즈니스 로직을 담당하는 AuthService이다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;

    // Firebase 토큰을 검증한 뒤 로컬 사용자와 연동 provider 스냅샷을 함께 동기화한다.
    @Transactional
    public AuthResponse loginOrSignUp(String idToken) {
        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();

            UserRecord userRecord = firebaseAuth.getUser(uid);
            String normalizedProvider = resolveSignInProvider(decodedToken, userRecord);
            String name = userRecord.getDisplayName();
            String picture = userRecord.getPhotoUrl();

            User user = userRepository.findByFirebaseUid(uid)
                    .map(existingUser -> {
                        existingUser.setEmail(email);
                        existingUser.setNickname(name != null ? name : "User_" + uid.substring(0, 5));
                        existingUser.setProfileImageUrl(picture);
                        existingUser.setAuthProvider(normalizedProvider);
                        existingUser.setStatus(UserStatus.ACTIVE);
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> {
                        log.info("신규 사용자 가입 진행: firebaseUid={}", uid);
                        User newUser = User.builder()
                                .firebaseUid(uid)
                                .email(email)
                                .nickname(name != null ? name : "User_" + uid.substring(0, 5))
                                .profileImageUrl(picture)
                                .authProvider(normalizedProvider)
                                .role(UserRole.USER)
                                .status(UserStatus.ACTIVE)
                                .build();
                        return userRepository.save(newUser);
                    });

            syncProviders(user, userRecord);
            return buildAuthResponse(user);
        } catch (FirebaseAuthException e) {
            log.error("Firebase 토큰 검증 중 오류 발생: {}", e.getMessage());
            throw ApiException.unauthorized("INVALID_TOKEN", "인증 오류가 발생했습니다: " + e.getAuthErrorCode());
        }
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    public LogoutResponse logout() {
        User user = getCurrentUser();
        log.info("사용자 로그아웃 처리 (RefreshToken 만료): userId={}", user.getId());

        try {
            firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
            return new LogoutResponse(true, "로그아웃 성공. 모든 세션이 만료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 로그아웃 처리 중 오류 발생: {}", e.getMessage());
            throw ApiException.internal("LOGOUT_FAILED", "로그아웃 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public WithdrawResponse withdraw() {
        User user = getCurrentUser();
        log.info("사용자 탈퇴 처리 시작 (Firebase 계정 삭제 포함): userId={}", user.getId());

        try {
            firebaseAuth.deleteUser(user.getFirebaseUid());

            user.setStatus(UserStatus.WITHDRAWN);
            userRepository.save(user);

            log.info("사용자 탈퇴 완료: userId={}", user.getId());
            return new WithdrawResponse("WITHDRAWN", "회원 탈퇴 및 Firebase 계정 삭제가 완료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 사용자 삭제 중 오류 발생: {}", e.getMessage());
            throw ApiException.internal("FIREBASE_WITHDRAW_FAILED", "Firebase 탈퇴 처리 중 오류가 발생했습니다.");
        } catch (Exception e) {
            log.error("사용자 DB 탈퇴 처리 중 오류 발생: {}", e.getMessage());
            throw ApiException.internal("WITHDRAW_FAILED", "DB 탈퇴 처리 중 오류가 발생했습니다.");
        }
    }

    // 마지막 연결 수단까지 끊어 계정이 고립되는 상황을 막고, 해제 후 기존 세션도 함께 무효화한다.
    @Transactional
    public SocialUnlinkResponse unlinkSocial(String provider) {
        User user = getCurrentUser();
        String normalizedProvider = normalizeProviderId(provider);
        log.info("소셜 계정 연결 해제 요청: userId={}, provider={}", user.getId(), normalizedProvider);

        List<UserSocialAccount> linkedAccounts = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user);
        UserSocialAccount target = userSocialAccountRepository.findByUserAndProvider(user, normalizedProvider)
                .orElseThrow(() -> ApiException.notFound("SOCIAL_ACCOUNT_NOT_FOUND", "연결된 소셜 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(target.getLinked())) {
            return new SocialUnlinkResponse(
                    normalizedProvider,
                    true,
                    getLinkedProviders(user),
                    normalizedProvider + " 계정은 이미 해제된 상태입니다."
            );
        }

        if (linkedAccounts.size() <= 1) {
            throw ApiException.conflict("LAST_SOCIAL_ACCOUNT", "마지막 소셜 계정은 해제할 수 없습니다. 다른 소셜 계정을 먼저 연결하거나 회원 탈퇴를 이용해주세요.");
        }

        target.setLinked(false);
        userSocialAccountRepository.save(target);

        List<String> remainingProviders = getLinkedProviders(user);
        user.setAuthProvider(remainingProviders.isEmpty() ? null : remainingProviders.get(0));
        userRepository.save(user);

        try {
            firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
        } catch (FirebaseAuthException e) {
            log.error("Firebase 소셜 연결 해제 중 오류 발생: {}", e.getMessage());
            throw ApiException.internal("UNLINK_FAILED", "연결 해제 중 오류가 발생했습니다.");
        }

        return new SocialUnlinkResponse(
                normalizedProvider,
                true,
                remainingProviders,
                normalizedProvider + " 계정 연결이 해제되었습니다."
        );
    }

    // 토큰 claim을 우선 신뢰하되, 누락된 경우 providerData까지 내려가서 대표 provider를 복구한다.
    private String resolveSignInProvider(FirebaseToken decodedToken, UserRecord userRecord) {
        Object firebaseClaim = decodedToken.getClaims().get("firebase");
        if (firebaseClaim instanceof Map<?, ?> firebaseMap) {
            Object signInProvider = firebaseMap.get("sign_in_provider");
            if (signInProvider instanceof String provider && !provider.isBlank()) {
                return normalizeProviderId(provider);
            }
        }
        return resolvePrimaryProvider(userRecord);
    }

    private String resolvePrimaryProvider(UserRecord userRecord) {
        UserInfo[] providerData = userRecord.getProviderData();
        if (providerData == null || providerData.length == 0) {
            return "FIREBASE";
        }

        for (UserInfo provider : providerData) {
            String providerId = provider.getProviderId();
            if (providerId == null || providerId.isBlank() || "firebase".equalsIgnoreCase(providerId)) {
                continue;
            }
            return normalizeProviderId(providerId);
        }

        return "FIREBASE";
    }

    // 단일 authProvider 필드와 별도로 다중 소셜 연동 테이블을 현재 Firebase 상태에 맞춰 갱신한다.
    private void syncProviders(User user, UserRecord userRecord) {
        UserInfo[] providerData = userRecord.getProviderData();
        if (providerData == null || providerData.length == 0) {
            syncSingleProvider(user, "FIREBASE", user.getFirebaseUid(), user.getEmail());
            return;
        }

        List<String> syncedProviders = new ArrayList<>();
        for (UserInfo provider : providerData) {
            String providerId = provider.getProviderId();
            if (providerId == null || providerId.isBlank() || "firebase".equalsIgnoreCase(providerId)) {
                continue;
            }

            String normalizedProvider = normalizeProviderId(providerId);
            syncedProviders.add(normalizedProvider);
            upsertSocialAccount(
                    user,
                    normalizedProvider,
                    provider.getUid(),
                    provider.getEmail() != null ? provider.getEmail() : user.getEmail()
            );
        }

        if (syncedProviders.isEmpty()) {
            syncSingleProvider(user, "FIREBASE", user.getFirebaseUid(), user.getEmail());
        }
    }

    private void syncSingleProvider(User user, String provider, String providerUserId, String providerEmail) {
        upsertSocialAccount(user, provider, providerUserId, providerEmail);
    }

    private void upsertSocialAccount(User user, String provider, String providerUserId, String providerEmail) {
        UserSocialAccount account = userSocialAccountRepository.findByUserAndProvider(user, provider)
                .orElseGet(() -> UserSocialAccount.builder()
                        .user(user)
                        .provider(provider)
                        .build());

        account.setProviderUserId(providerUserId);
        account.setProviderEmail(providerEmail);
        account.setLinked(true);
        account.setLastLinkedAt(Instant.now());
        userSocialAccountRepository.save(account);
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                user.getAuthProvider(),
                getLinkedProviders(user),
                user.getRole().name()
        );
    }

    private List<String> getLinkedProviders(User user) {
        List<String> linkedProviders = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user).stream()
                .map(UserSocialAccount::getProvider)
                .toList();
        if (!linkedProviders.isEmpty()) {
            return linkedProviders;
        }
        if (user.getAuthProvider() != null && !user.getAuthProvider().isBlank()) {
            return List.of(user.getAuthProvider());
        }
        return List.of();
    }

    private String normalizeProviderId(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "google.com" -> "GOOGLE";
            case "apple.com" -> "APPLE";
            case "facebook.com" -> "FACEBOOK";
            case "github.com" -> "GITHUB";
            case "password" -> "EMAIL";
            case "phone" -> "PHONE";
            case "google", "apple", "facebook", "github", "email", "firebase" -> providerId.toUpperCase();
            default -> providerId.toUpperCase().replace('.', '_');
        };
    }

}
