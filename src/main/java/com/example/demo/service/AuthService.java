package com.example.demo.service;
import com.example.demo.entity.User;
import com.example.demo.entity.UserSocialAccount;
import com.example.demo.entity.enums.UserRole;
import com.example.demo.entity.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSocialAccountRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;

    @Transactional
    public Map<String, Object> loginOrSignUp(String idToken) throws Exception {
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
                        log.info("신규 사용자 가입 진행: {}", email);
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
            throw new RuntimeException("인증 오류가 발생했습니다: " + e.getAuthErrorCode());
        }
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    public Map<String, Object> logout() {
        User user = getCurrentUser();
        log.info("사용자 로그아웃 처리 (RefreshToken 만료): {}", user.getEmail());

        try {
            firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
            return Map.of("revoked", true, "message", "로그아웃 성공. 모든 세션이 만료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 로그아웃 처리 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("로그아웃 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public Map<String, Object> withdraw() {
        User user = getCurrentUser();
        log.info("사용자 탈퇴 처리 시작 (Firebase 계정 삭제 포함): {}", user.getEmail());

        try {
            firebaseAuth.deleteUser(user.getFirebaseUid());

            user.setStatus(UserStatus.WITHDRAWN);
            userRepository.save(user);

            log.info("사용자 탈퇴 완료: {}", user.getEmail());
            return Map.of("status", "WITHDRAWN", "message", "회원 탈퇴 및 Firebase 계정 삭제가 완료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 사용자 삭제 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("Firebase 탈퇴 처리 중 오류가 발생했습니다.");
        } catch (Exception e) {
            log.error("사용자 DB 탈퇴 처리 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("DB 탈퇴 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public Map<String, Object> unlinkSocial(String provider) {
        User user = getCurrentUser();
        String normalizedProvider = normalizeProviderId(provider);
        log.info("소셜 계정 연결 해제 요청: {}, Provider: {}", user.getEmail(), normalizedProvider);

        List<UserSocialAccount> linkedAccounts = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user);
        UserSocialAccount target = userSocialAccountRepository.findByUserAndProvider(user, normalizedProvider)
                .orElseThrow(() -> new RuntimeException("연결된 소셜 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(target.getLinked())) {
            return Map.of(
                    "provider", normalizedProvider,
                    "unlinked", true,
                    "linkedProviders", getLinkedProviders(user),
                    "message", normalizedProvider + " 계정은 이미 해제된 상태입니다."
            );
        }

        if (linkedAccounts.size() <= 1) {
            throw new RuntimeException("마지막 소셜 계정은 해제할 수 없습니다. 다른 소셜 계정을 먼저 연결하거나 회원 탈퇴를 이용해주세요.");
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
            throw new RuntimeException("연결 해제 중 오류가 발생했습니다.");
        }

        return Map.of(
                "provider", normalizedProvider,
                "unlinked", true,
                "linkedProviders", remainingProviders,
                "message", normalizedProvider + " 계정 연결이 해제되었습니다."
        );
    }

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

    private Map<String, Object> buildAuthResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "firebaseUid", user.getFirebaseUid(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                "provider", user.getAuthProvider(),
                "linkedProviders", getLinkedProviders(user),
                "role", user.getRole().name()
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
