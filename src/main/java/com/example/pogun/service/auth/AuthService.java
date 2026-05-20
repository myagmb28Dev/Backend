package com.example.pogun.service.auth;

import com.example.pogun.config.web.RequestHostResolver;
import com.example.pogun.config.firebase.FirebaseAuthProperties;
import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.OnboardingCompleteRequest;
import com.example.pogun.dto.auth.SocialUnlinkResponse;
import com.example.pogun.dto.auth.WithdrawResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.location.RegionResponse;
import com.example.pogun.entity.user.PendingSocialSignup;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.PendingSocialSignupRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.example.pogun.service.location.KakaoLocalService;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
/**
 * 도메인 비즈니스 로직을 담당하는 AuthService이다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REGISTRATION_COMPLETED = "COMPLETED";
    private static final String REGISTRATION_PENDING_ONBOARDING = "PENDING_ONBOARDING";
    private static final long PENDING_SIGNUP_TTL_SECONDS = 60L * 60L * 24L;
    private static final Pattern HANGUL_PATTERN = Pattern.compile(".*\\p{IsHangul}.*");

    private final FirebaseAuth firebaseAuth;
    private final FirebaseAuthProperties firebaseAuthProperties;
    private final FirebaseIdentityService firebaseIdentityService;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final PendingSocialSignupRepository pendingSocialSignupRepository;
    private final KakaoLocalService kakaoLocalService;
    private final UserPresenceService userPresenceService;
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    // Firebase 토큰을 검증한 뒤 로컬 사용자와 연동 provider 스냅샷을 함께 동기화한다.
    @Transactional
    public AuthResponse loginOrSignUp(String idToken, HttpServletRequest httpRequest) {
        log.info("[LOGIN] loginOrSignUp started, token_length={}", idToken.length());
        try {
            long verifyStart = System.currentTimeMillis();
            FirebaseIdentityService.FirebaseIdentity identity = firebaseIdentityService.verifyIdToken(idToken);
            long verifyEnd = System.currentTimeMillis();
            log.info("[LOGIN] Firebase token verified in {}ms", verifyEnd - verifyStart);
            
            String uid = identity.uid();
            String email = identity.email();
            String normalizedProvider = FirebaseProviderNormalizer.resolvePrimaryProvider(identity, "FIREBASE");
            String picture = identity.photoUrl();
            String resolvedNickname = resolveNickname(identity, email, uid);

            User user = userRepository.findByFirebaseUid(uid)
                    .map(existingUser -> {
                        existingUser.setEmail(email);
                        existingUser.setNickname(resolveNicknameForExistingUser(existingUser.getNickname(), resolvedNickname));
                        fillProfileImageIfMissing(existingUser, picture);
                        existingUser.setAuthProvider(normalizedProvider);
                        existingUser.setLastActiveAt(Instant.now());
                        existingUser.setStatus(UserStatus.ACTIVE);
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> userRepository.findByEmail(email)
                            .map(existingByEmail -> {
                                // 에뮬레이터/소셜 재연동 등으로 UID가 바뀐 경우 기존 계정에 새 UID를 연결한다.
                                existingByEmail.setFirebaseUid(uid);
                                existingByEmail.setNickname(resolveNicknameForExistingUser(existingByEmail.getNickname(), resolvedNickname));
                                fillProfileImageIfMissing(existingByEmail, picture);
                                existingByEmail.setAuthProvider(normalizedProvider);
                                existingByEmail.setLastActiveAt(Instant.now());
                                existingByEmail.setStatus(UserStatus.ACTIVE);
                                return userRepository.save(existingByEmail);
                            })
                    .orElse(null));

            if (user == null) {
                PendingSocialSignup pending = upsertPendingSignup(identity, normalizedProvider, resolvedNickname, picture);
                log.info("소셜 인증 완료, 온보딩 대기 상태로 저장: firebaseUid={}, pendingSignupId={}", uid, pending.getId());
                return buildPendingAuthResponse(pending);
            }

            syncProviders(user, identity);
            pendingSocialSignupRepository.deleteByFirebaseUid(uid);
            touchAndPublishPresenceSafely(uid, RequestHostResolver.resolve(httpRequest));
            return buildAuthResponse(user);
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            log.error("Firebase 토큰 검증 중 오류 발생: {}", e.getMessage());
            String detail = e instanceof FirebaseAuthException firebaseAuthException
                    ? String.valueOf(firebaseAuthException.getAuthErrorCode())
                    : e.getMessage();
            throw ApiException.unauthorized("INVALID_TOKEN", "인증 오류가 발생했습니다: " + detail);
        } catch (RuntimeException e) {
            log.error("소셜 로그인 처리 중 런타임 오류 발생", e);
            throw ApiException.unauthorized("INVALID_TOKEN", "토큰 검증에 실패했습니다. 다시 로그인해주세요.");
        } catch (Exception e) {
            log.error("소셜 로그인 처리 중 예기치 않은 오류 발생", e);
            throw ApiException.internal("AUTH_LOGIN_FAILED", "로그인 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public AuthResponse completeOnboarding(OnboardingCompleteRequest request) {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RegionResponse region = kakaoLocalService.resolveRegion(request.getX(), request.getY());

        if (userRepository.findByFirebaseUid(firebaseUid).isPresent()) {
            throw ApiException.conflict("ALREADY_REGISTERED", "이미 회원가입이 완료된 사용자입니다.");
        }

        PendingSocialSignup pending = pendingSocialSignupRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.forbidden("ONBOARDING_REQUIRED", "소셜 인증 후 온보딩을 먼저 시작해주세요."));

        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(Instant.now())) {
            pendingSocialSignupRepository.delete(pending);
            throw ApiException.unauthorized("PENDING_SIGNUP_EXPIRED", "온보딩 세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        if (userRepository.findByEmail(pending.getEmail()).isPresent()) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다. 다시 로그인해주세요.");
        }

        User user = User.builder()
                .firebaseUid(pending.getFirebaseUid())
                .email(pending.getEmail())
                .nickname(pending.getNickname())
                .profileImageUrl(pending.getProfileImageUrl())
                .region(region.addressName())
                .regionType(region.regionType())
                .regionAddressName(region.addressName())
                .region1DepthName(region.region1DepthName())
                .region2DepthName(region.region2DepthName())
                .region3DepthName(region.region3DepthName())
                .authProvider(pending.getProvider())
                .lastActiveAt(Instant.now())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        User saved = userRepository.save(user);

        for (String provider : parseLinkedProviders(pending.getLinkedProviders())) {
            upsertSocialAccount(saved, provider, saved.getFirebaseUid(), saved.getEmail());
        }

        // Remove any stale pending rows sharing the same email (different Firebase UIDs).
        pendingSocialSignupRepository.deleteByEmailIgnoreCase(pending.getEmail());
        touchAndPublishPresenceSafely(saved.getFirebaseUid(), null);
        log.info("온보딩 완료 및 정식 회원 생성: userId={}, firebaseUid={}", saved.getId(), saved.getFirebaseUid());
        return buildAuthResponse(saved);
    }

    @Transactional
    public AuthResponse loginAdmin(String idToken, HttpServletRequest httpRequest) {
        try {
            FirebaseIdentityService.FirebaseIdentity identity = firebaseIdentityService.verifyIdToken(idToken);
            String uid = identity.uid();
            String email = identity.email();
            String normalizedProvider = FirebaseProviderNormalizer.resolvePrimaryProvider(identity, FirebaseProviderNormalizer.FIREBASE);
            String resolvedNickname = resolveNickname(identity, email, uid);

            User user = userRepository.findByFirebaseUid(uid)
                    .orElseGet(() -> userRepository.findByEmail(email)
                            .map(existingByEmail -> {
                                existingByEmail.setFirebaseUid(uid);
                                return userRepository.save(existingByEmail);
                            })
                            .orElseThrow(() -> ApiException.forbidden("ADMIN_ONLY", "관리자 계정만 로그인할 수 있습니다.")));

            if (user.getRole() != UserRole.ADMIN) {
                throw ApiException.forbidden("ADMIN_ONLY", "관리자 계정만 로그인할 수 있습니다.");
            }
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw ApiException.forbidden(
                        user.getStatus() == UserStatus.BANNED ? "USER_BANNED" : "USER_WITHDRAWN",
                        user.getStatus() == UserStatus.BANNED ? "제재된 관리자는 이용할 수 없습니다." : "탈퇴한 관리자는 이용할 수 없습니다."
                );
            }

            user.setEmail(email);
            user.setNickname(resolveNicknameForExistingUser(user.getNickname(), resolvedNickname));
            fillProfileImageIfMissing(user, identity.photoUrl());
            user.setAuthProvider(normalizedProvider);
            user.setLastActiveAt(Instant.now());
            User saved = userRepository.save(user);

            syncProviders(saved, identity);
            touchAndPublishPresenceSafely(saved.getFirebaseUid(), RequestHostResolver.resolve(httpRequest));
            return buildAuthResponse(saved);
        } catch (ApiException e) {
            throw e;
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            log.error("관리자 Firebase 토큰 검증 중 오류 발생: {}", e.getMessage());
            String detail = e instanceof FirebaseAuthException firebaseAuthException
                    ? String.valueOf(firebaseAuthException.getAuthErrorCode())
                    : e.getMessage();
            throw ApiException.unauthorized("INVALID_TOKEN", "인증 오류가 발생했습니다: " + detail);
        } catch (RuntimeException e) {
            log.error("관리자 로그인 처리 중 런타임 오류 발생", e);
            throw ApiException.unauthorized("INVALID_TOKEN", "토큰 검증에 실패했습니다. 다시 로그인해주세요.");
        } catch (Exception e) {
            log.error("관리자 로그인 처리 중 예기치 않은 오류 발생", e);
            throw ApiException.internal("ADMIN_AUTH_FAILED", "관리자 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    private String resolveNickname(FirebaseIdentityService.FirebaseIdentity identity, String email, String uid) {
        String displayName = resolveDisplayName(identity, email);
        if (displayName == null || displayName.isBlank()) {
            displayName = resolveDisplayNameFromFirebase(uid);
        }
        if (displayName != null && !displayName.isBlank()) {
            return FirebaseDisplayNameNormalizer.normalize(displayName);
        }
        return "User_" + uid.substring(0, Math.min(5, uid.length()));
    }

    private String resolveDisplayNameFromFirebase(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return null;
        }
        try {
            UserRecord firebaseUser = firebaseAuth.getUser(firebaseUid);
            if (firebaseUser == null) {
                return null;
            }
            return normalizeNameCandidate(firebaseUser.getDisplayName());
        } catch (FirebaseAuthException e) {
            log.debug("Firebase displayName lookup failed. firebaseUid={}, reason={}", firebaseUid, e.getMessage());
            return null;
        }
    }

    private String resolveNicknameForExistingUser(String currentNickname, String incomingNickname) {
        if (incomingNickname == null || incomingNickname.isBlank()) {
            return currentNickname;
        }
        if (currentNickname == null || currentNickname.isBlank()) {
            return incomingNickname;
        }
        if (isFallbackNickname(incomingNickname)) {
            return currentNickname;
        }
        if (containsHangul(currentNickname) && !containsHangul(incomingNickname)) {
            return currentNickname;
        }
        return incomingNickname;
    }

    private void fillProfileImageIfMissing(User user, String incomingProfileImageUrl) {
        if (user == null || incomingProfileImageUrl == null || incomingProfileImageUrl.isBlank()) {
            return;
        }
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) {
            return;
        }
        user.setProfileImageUrl(incomingProfileImageUrl.trim());
    }

    private String resolveDisplayName(FirebaseIdentityService.FirebaseIdentity identity, String email) {
        if (identity == null) {
            return null;
        }
        String normalizedDisplayName = normalizeNameCandidate(identity.displayName());
        String normalizedClaimName = normalizeNameCandidate(claimAsString(identity.claims(), "name"));
        String normalizedClaimGivenName = normalizeNameCandidate(claimAsString(identity.claims(), "given_name"));
        String normalizedClaimFamilyName = normalizeNameCandidate(claimAsString(identity.claims(), "family_name"));

        String nameFromClaims = composeClaimName(normalizedClaimGivenName, normalizedClaimFamilyName);
        if (normalizedClaimName != null && !normalizedClaimName.isBlank()) {
            nameFromClaims = normalizedClaimName;
        }

        if (looksLikeEmailAlias(normalizedDisplayName, email) && nameFromClaims != null && !nameFromClaims.isBlank()) {
            return nameFromClaims;
        }
        return normalizedDisplayName != null && !normalizedDisplayName.isBlank() ? normalizedDisplayName : nameFromClaims;
    }

    private String composeClaimName(String givenName, String familyName) {
        if (givenName == null || givenName.isBlank()) {
            return familyName;
        }
        if (familyName == null || familyName.isBlank()) {
            return givenName;
        }
        return givenName + " " + familyName;
    }

    private String claimAsString(Map<String, Object> claims, String key) {
        if (claims == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = claims.get(key);
        if (!(value instanceof String stringValue)) {
            return null;
        }
        return stringValue;
    }

    private String normalizeNameCandidate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean looksLikeEmailAlias(String displayName, String email) {
        if (displayName == null || displayName.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return false;
        }
        String localPart = email.substring(0, atIndex).toLowerCase();
        String normalizedDisplayName = displayName.toLowerCase().replace(" ", "");
        return !localPart.isBlank() && normalizedDisplayName.equals(localPart);
    }

    private boolean isFallbackNickname(String nickname) {
        return nickname != null && nickname.startsWith("User_");
    }

    private boolean containsHangul(String value) {
        return value != null && HANGUL_PATTERN.matcher(value).matches();
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    public LogoutResponse logout() {
        User user = getCurrentUser();
        String currentIdToken = resolveCurrentIdToken();
        log.info("사용자 로그아웃 처리 (RefreshToken 만료): userId={}", user.getId());

        try {
            if (!firebaseAuthProperties.isEmulatorMode() && !firebaseAuthProperties.isAllowEmulator()) {
                firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
            }
            firebaseIdentityService.revokeTokenLocally(currentIdToken);
            try {
                userPresenceService.forceOffline(user.getFirebaseUid());
            } catch (RuntimeException e) {
                log.warn("로그아웃 presence forceOffline 실패(userId={}): {}", user.getId(), e.getClass().getSimpleName());
            }
            try {
                noticeChatServiceProvider.getObject().publishPresenceUpdates(user);
            } catch (RuntimeException e) {
                log.warn("로그아웃 presence publish 실패(userId={}): {}", user.getId(), e.getClass().getSimpleName());
            }
            return new LogoutResponse(true, "로그아웃 성공. 모든 세션이 만료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 로그아웃 처리 중 오류 발생: {}", e.getMessage());
            throw ApiException.internal("LOGOUT_FAILED", "로그아웃 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public WithdrawResponse withdraw() {
        User user = getCurrentUser();
        String currentIdToken = resolveCurrentIdToken();
        log.info("사용자 탈퇴 처리 시작 (Firebase 계정 삭제 포함): userId={}", user.getId());

        try {
            firebaseAuth.deleteUser(user.getFirebaseUid());

            user.setStatus(UserStatus.WITHDRAWN);
            userRepository.save(user);
            firebaseIdentityService.revokeTokenLocally(currentIdToken);

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
        String currentIdToken = resolveCurrentIdToken();
        String normalizedProvider = FirebaseProviderNormalizer.normalize(provider);
        if (!FirebaseProviderNormalizer.isExternalProvider(normalizedProvider)) {
            throw ApiException.badRequest("UNSUPPORTED_SOCIAL_PROVIDER", "소셜 제공자만 연동 해제할 수 있습니다.");
        }
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
            firebaseIdentityService.revokeTokenLocally(currentIdToken);
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

    private String resolveCurrentIdToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object credentials = authentication.getCredentials();
        return credentials instanceof String token && !token.isBlank() ? token : null;
    }

    // 단일 authProvider 필드와 별도로 다중 소셜 연동 테이블을 현재 Firebase 상태에 맞춰 갱신한다.
    private void syncProviders(User user, FirebaseIdentityService.FirebaseIdentity identity) {
        List<String> syncedProviders = FirebaseProviderNormalizer.resolveLinkedProviders(
                identity,
                identity.signInProvider() != null ? identity.signInProvider() : "FIREBASE"
        );
        for (String normalizedProvider : syncedProviders) {
            if (!FirebaseProviderNormalizer.isExternalProvider(normalizedProvider)) {
                continue;
            }
            FirebaseIdentityService.ProviderIdentity provider = findProviderIdentity(identity, normalizedProvider);
            upsertSocialAccount(
                    user,
                    normalizedProvider,
                    provider != null && provider.uid() != null ? provider.uid() : user.getFirebaseUid(),
                    provider != null && provider.email() != null ? provider.email() : user.getEmail()
            );
        }
        Set<String> syncedExternalProviders = Set.copyOf(syncedProviders.stream()
                .filter(FirebaseProviderNormalizer::isExternalProvider)
                .toList());
        unlinkStaleProviders(user, syncedExternalProviders);
    }

    private PendingSocialSignup upsertPendingSignup(
            FirebaseIdentityService.FirebaseIdentity identity,
            String normalizedProvider,
            String resolvedNickname,
            String picture
    ) {
        List<String> linkedProviders = FirebaseProviderNormalizer.resolveLinkedProviders(identity, normalizedProvider);
        PendingSocialSignup pending = pendingSocialSignupRepository.findByFirebaseUid(identity.uid())
                .orElseGet(() -> PendingSocialSignup.builder()
                        .firebaseUid(identity.uid())
                        .build());

        pending.setEmail(identity.email());
        pending.setNickname(resolveNicknameForExistingUser(pending.getNickname(), resolvedNickname));
        pending.setProfileImageUrl(picture);
        pending.setProvider(normalizedProvider);
        pending.setLinkedProviders(String.join(",", linkedProviders));
        pending.setExpiresAt(Instant.now().plusSeconds(PENDING_SIGNUP_TTL_SECONDS));
        return pendingSocialSignupRepository.save(pending);
    }

    private List<String> parseLinkedProviders(String linkedProviders) {
        if (linkedProviders == null || linkedProviders.isBlank()) {
            return List.of();
        }
        return Arrays.stream(linkedProviders.split(","))
                .map(String::trim)
                .filter(provider -> !provider.isBlank())
                .distinct()
                .toList();
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

    private FirebaseIdentityService.ProviderIdentity findProviderIdentity(FirebaseIdentityService.FirebaseIdentity identity, String normalizedProvider) {
        if (identity == null || identity.providers() == null) {
            return null;
        }
        return identity.providers().stream()
                .filter(provider -> provider != null && provider.providerId() != null)
                .filter(provider -> FirebaseProviderNormalizer.normalize(provider.providerId()).equals(normalizedProvider))
                .findFirst()
                .orElse(null);
    }

    private void unlinkStaleProviders(User user, Set<String> syncedProviders) {
        userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user).stream()
                .filter(account -> !syncedProviders.contains(account.getProvider()))
                .forEach(account -> {
                    account.setLinked(false);
                    userSocialAccountRepository.save(account);
                });
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                user.getId(),
                null,
                user.getFirebaseUid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                FirebaseProviderNormalizer.normalize(user.getAuthProvider()),
                getLinkedProviders(user),
                user.getRole().name(),
                REGISTRATION_COMPLETED,
                user.getRegion() != null ? user.getRegion() : "",
                buildRegionResponse(user)
        );
    }

    private AuthResponse buildPendingAuthResponse(PendingSocialSignup pending) {
        return new AuthResponse(
                null,
                pending.getId(),
                pending.getFirebaseUid(),
                pending.getEmail(),
                pending.getNickname(),
                pending.getProfileImageUrl() != null ? pending.getProfileImageUrl() : "",
                pending.getProvider(),
                parseLinkedProviders(pending.getLinkedProviders()),
                null,
                REGISTRATION_PENDING_ONBOARDING,
                "",
                null
        );
    }

    private RegionResponse buildRegionResponse(User user) {
        if (user.getRegionAddressName() == null || user.getRegionAddressName().isBlank()) {
            return null;
        }
        return new RegionResponse(
                user.getRegionType(),
                user.getRegionAddressName(),
                user.getRegion1DepthName(),
                user.getRegion2DepthName(),
                user.getRegion3DepthName()
        );
    }

    private List<String> getLinkedProviders(User user) {
        List<String> linkedProviders = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user).stream()
                .map(UserSocialAccount::getProvider)
                .filter(FirebaseProviderNormalizer::isExternalProvider)
                .toList();
        if (!linkedProviders.isEmpty()) {
            return linkedProviders;
        }
        return List.of(FirebaseProviderNormalizer.normalize(user.getAuthProvider()));
    }

    private void touchPresenceSafely(String firebaseUid, String sessionScopeHost) {
        userPresenceService.touchFromAuthenticationSafely(firebaseUid, sessionScopeHost);
    }

    private void touchAndPublishPresenceSafely(String firebaseUid, String sessionScopeHost) {
        touchPresenceSafely(firebaseUid, sessionScopeHost);
        try {
            noticeChatServiceProvider.getObject().publishPresenceUpdatesByFirebaseUid(firebaseUid);
        } catch (RuntimeException e) {
            log.warn("로그인 presence publish 실패(firebaseUid={}): {}", firebaseUid, e.getClass().getSimpleName());
        }
    }

}

