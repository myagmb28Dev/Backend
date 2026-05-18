package com.example.pogun.service.adminauth;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.dto.admin.auth.AdminAuthSessionAdminResponse;
import com.example.pogun.dto.admin.auth.AdminAuthSessionResponse;
import com.example.pogun.dto.admin.auth.AdminLoginResponse;
import com.example.pogun.dto.admin.auth.AdminPasskeyCredentialRequest;
import com.example.pogun.dto.admin.auth.AdminPasskeyOptionsResponse;
import com.example.pogun.dto.admin.auth.AdminSessionStateResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.admin.AdminAuthChallenge;
import com.example.pogun.entity.admin.AdminPasskey;
import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminAuthChallengeType;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.admin.enums.AdminSessionStage;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.admin.AdminAuthChallengeRepository;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.repository.admin.AdminSessionRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.auth.FirebaseDisplayNameNormalizer;
import com.example.pogun.service.auth.FirebaseIdentityService;
import com.example.pogun.service.auth.FirebaseProviderNormalizer;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuth firebaseAuth;
    private final FirebaseIdentityService firebaseIdentityService;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final AdminPasskeyRepository adminPasskeyRepository;
    private final AdminAuthChallengeRepository adminAuthChallengeRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final AdminSessionTokenService adminSessionTokenService;
    private final AdminPermissionService adminPermissionService;
    private final AdminEmailVerificationService adminEmailVerificationService;
    private final AdminSecurityService adminSecurityService;
    private final AdminWebAuthnService adminWebAuthnService;
    private final AdminAuditService adminAuditService;
    private final AdminConsoleProperties adminConsoleProperties;

    public AdminLoginResponse loginWithFirebaseToken(String firebaseIdToken, HttpServletRequest request) {
        FirebaseIdentityService.FirebaseIdentity identity;
        try {
            identity = firebaseIdentityService.verifyIdToken(firebaseIdToken, true);
        } catch (Exception e) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "유효한 Firebase 소셜 로그인 토큰이 필요합니다.");
        }

        if (!FirebaseProviderNormalizer.isAdminLoginAllowed(identity)) {
            throw ApiException.forbidden("ADMIN_SOCIAL_SIGN_IN_REQUIRED", "관리자 로그인은 Google 또는 Apple 소셜 로그인만 허용됩니다.");
        }

        String firebaseUid = identity.uid();
        String email = identity.email();
        if (firebaseUid == null || firebaseUid.isBlank() || email == null || email.isBlank()) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "소셜 로그인 사용자 정보를 확인할 수 없습니다.");
        }

        User admin = resolveAdminUser(firebaseUid, email);
        admin = syncAdminProfileIfMissing(admin, identity, firebaseUid);
        syncExternalProviders(admin, identity);

        if (!admin.isAdminEmailVerificationRequired() && admin.getAdminEmailVerifiedAt() == null) {
            admin.setAdminEmailVerificationRequired(true);
            admin.setAdminEmailVerificationSentAt(null);
            admin = userRepository.save(admin);
        }

        if (admin.isAdminEmailVerificationRequired()) {
            if (admin.getAdminEmailVerificationSentAt() == null) {
                return sendInitialAdminEmailVerification(admin, firebaseUid, firebaseIdToken, request);
            }
            boolean emailVerified = resolveEmailVerified(firebaseUid, false);
            if (!emailVerified) {
                return new AdminLoginResponse(
                        "EMAIL_VERIFICATION_REQUIRED",
                        false,
                        null,
                        null
                );
            }
            admin.setAdminEmailVerificationRequired(false);
            admin.setAdminEmailVerifiedAt(Instant.now());
            admin.setAdminEmailVerificationSentAt(null);
            admin = userRepository.save(admin);
        } else if (!resolveEmailVerified(firebaseUid, false)) {
            admin.setAdminEmailVerificationRequired(true);
            admin.setAdminEmailVerifiedAt(null);
            admin.setAdminEmailVerificationSentAt(null);
            admin = userRepository.save(admin);
            return sendInitialAdminEmailVerification(admin, firebaseUid, firebaseIdToken, request);
        }

        return buildLoginResponse(admin, request, false);
    }

    @Transactional
    public AdminPasskeyOptionsResponse beginPasskeyRegistration(HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.PASSKEY_ENROLL);
        User admin = session.getUser();
        String rpId = adminWebAuthnService.resolveRpId(request);
        log.info("PassKey registration options requested. userId={}, sessionId={}, stage={}, origin={}, host={}, rpId={}",
                admin != null ? admin.getId() : null,
                session.getId(),
                session.getStage(),
                request != null ? request.getHeader("Origin") : null,
                request != null ? request.getServerName() : null,
                rpId);
        invalidatePendingChallenges(session, AdminAuthChallengeType.PASSKEY_REGISTRATION);
        var options = adminWebAuthnService.startRegistration(admin, request);
        AdminAuthChallenge challenge = adminAuthChallengeRepository.save(AdminAuthChallenge.builder()
                .session(session)
                .user(admin)
                .type(AdminAuthChallengeType.PASSKEY_REGISTRATION)
                .requestJson(serializeOptions(options))
                .expiresAt(Instant.now().plusSeconds(adminConsoleProperties.getChallengeTtlSeconds()))
                .build());
        log.info("PassKey registration challenge issued. userId={}, sessionId={}, challengeId={}, challengeType={}, expiresAt={}",
                admin != null ? admin.getId() : null,
                session.getId(),
                challenge.getId(),
                challenge.getType(),
                challenge.getExpiresAt());
        return new AdminPasskeyOptionsResponse(challenge.getId(), readJson(serializeCreateOptions(options)));
    }

    @Transactional
    public AdminAuthSessionResponse finishPasskeyRegistration(AdminPasskeyCredentialRequest requestBody, HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.PASSKEY_ENROLL);
        AdminAuthChallenge challenge = getChallenge(session, requestBody.getChallengeId(), AdminAuthChallengeType.PASSKEY_REGISTRATION);
        log.info("PassKey registration verify requested. userId={}, sessionId={}, stage={}, challengeId={}, challengeType={}, challengeExpiresAt={}, challengeUsedAt={}, origin={}, host={}",
                session.getUser() != null ? session.getUser().getId() : null,
                session.getId(),
                session.getStage(),
                challenge.getId(),
                challenge.getType(),
                challenge.getExpiresAt(),
                challenge.getUsedAt(),
                request != null ? request.getHeader("Origin") : null,
                request != null ? request.getServerName() : null);
        try {
            AdminWebAuthnService.RegistrationFinishPayload result = adminWebAuthnService.finishRegistration(
                    challenge.getRequestJson(),
                    toJsonString(requestBody.getCredential()),
                    request
            );
            adminPasskeyRepository.save(AdminPasskey.builder()
                    .user(session.getUser())
                    .credentialId(result.credentialId())
                    .publicKeyCose(result.publicKeyCose())
                    .signatureCount(result.signatureCount())
                    .rpId(result.rpId())
                    .lastUsedAt(Instant.now())
                    .build());
            challenge.setUsedAt(Instant.now());
            session.setStage(AdminSessionStage.AUTHENTICATED);
            session.setExpiresAt(Instant.now().plusSeconds(adminConsoleProperties.getSessionTtlSeconds()));
            adminAuthChallengeRepository.save(challenge);
            adminSessionRepository.save(session);
            auditSafely("ADMIN_PASSKEY_REGISTERED", "ADMIN_USER", session.getUser().getId().toString(), null, Map.of("credentialId", result.credentialId()), null);
            log.info("PassKey registration verify succeeded. userId={}, sessionId={}, challengeId={}, credentialId={}, rpId={}",
                    session.getUser() != null ? session.getUser().getId() : null,
                    session.getId(),
                    challenge.getId(),
                    result.credentialId(),
                    result.rpId());
            return toSessionResponse(null, session, adminPermissionService.getPermissions(session.getUser()));
        } catch (Exception e) {
            String failureCode = mapPasskeyFailureCode(e);
            log.warn("PassKey registration verify failed. failureCode={}, userId={}, sessionId={}, stage={}, challengeId={}, challengeType={}, challengeExpiresAt={}, challengeUsedAt={}, origin={}, host={}, reason={}",
                    failureCode,
                    session.getUser() != null ? session.getUser().getId() : null,
                    session.getId(),
                    session.getStage(),
                    challenge.getId(),
                    challenge.getType(),
                    challenge.getExpiresAt(),
                    challenge.getUsedAt(),
                    request != null ? request.getHeader("Origin") : null,
                    request != null ? request.getServerName() : null,
                    e.getMessage(),
                    e);
            throw ApiException.forbidden(failureCode, "PassKey 등록 검증에 실패했습니다.");
        }
    }

    @Transactional
    public AdminAuthSessionResponse verifyMfa(AdminPasskeyCredentialRequest requestBody, HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.MFA_PENDING);
        AdminAuthChallenge challenge = getChallenge(session, requestBody.getChallengeId(), AdminAuthChallengeType.PASSKEY_ASSERTION);
        log.info("PassKey MFA verify requested. userId={}, sessionId={}, stage={}, challengeId={}, challengeType={}, challengeExpiresAt={}, challengeUsedAt={}, origin={}, host={}",
                session.getUser() != null ? session.getUser().getId() : null,
                session.getId(),
                session.getStage(),
                challenge.getId(),
                challenge.getType(),
                challenge.getExpiresAt(),
                challenge.getUsedAt(),
                request != null ? request.getHeader("Origin") : null,
                request != null ? request.getServerName() : null);
        try {
            AdminWebAuthnService.AssertionFinishPayload result = adminWebAuthnService.finishAssertion(
                    challenge.getRequestJson(),
                    toJsonString(requestBody.getCredential()),
                    request
            );
            String requestRpId = adminWebAuthnService.resolveRpId(request);
            adminPasskeyRepository.findByCredentialId(result.credentialId()).ifPresent(passkey -> {
                if (passkey.getRpId() != null && !passkey.getRpId().isBlank()
                        && !passkey.getRpId().equalsIgnoreCase(requestRpId)) {
                    throw ApiException.forbidden("PASSKEY_RP_MISMATCH", "현재 도메인과 일치하지 않는 PassKey입니다. 도메인별로 다시 등록해 주세요.");
                }
                passkey.setSignatureCount(result.signatureCount());
                passkey.setLastUsedAt(Instant.now());
                adminPasskeyRepository.save(passkey);
            });
            challenge.setUsedAt(Instant.now());
            session.setStage(AdminSessionStage.AUTHENTICATED);
            session.setExpiresAt(Instant.now().plusSeconds(adminConsoleProperties.getSessionTtlSeconds()));
            adminAuthChallengeRepository.save(challenge);
            adminSessionRepository.save(session);
            auditSafely("ADMIN_LOGIN_AUTHENTICATED", "ADMIN_USER", session.getUser().getId().toString(), null, Map.of("email", session.getUser().getEmail()), null);
            log.info("PassKey MFA verify succeeded. userId={}, sessionId={}, challengeId={}, credentialId={}, rpId={}",
                    session.getUser() != null ? session.getUser().getId() : null,
                    session.getId(),
                    challenge.getId(),
                    result.credentialId(),
                    result.rpId());
            return toSessionResponse(null, session, adminPermissionService.getPermissions(session.getUser()));
        } catch (Exception e) {
            String failureCode = mapPasskeyFailureCode(e);
            log.warn("PassKey MFA verify failed. failureCode={}, userId={}, sessionId={}, stage={}, challengeId={}, challengeType={}, challengeExpiresAt={}, challengeUsedAt={}, origin={}, host={}, reason={}",
                    failureCode,
                    session.getUser() != null ? session.getUser().getId() : null,
                    session.getId(),
                    session.getStage(),
                    challenge.getId(),
                    challenge.getType(),
                    challenge.getExpiresAt(),
                    challenge.getUsedAt(),
                    request != null ? request.getHeader("Origin") : null,
                    request != null ? request.getServerName() : null,
                    e.getMessage(),
                    e);
            throw ApiException.forbidden(failureCode, "PassKey 검증에 실패했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public AdminSessionStateResponse session() {
        try {
            AdminPrincipal principal = adminSecurityService.getCurrentPrincipal();
            AdminSession session = adminSessionRepository.findById(principal.sessionId())
                    .orElseThrow(() -> ApiException.unauthorized("ADMIN_SESSION_REQUIRED", "관리자 세션이 필요합니다."));
            return new AdminSessionStateResponse(
                    session.getStage() == AdminSessionStage.AUTHENTICATED,
                    session.getStage().name(),
                    toAdminInfo(session.getUser()),
                    principal.permissions().stream().map(Enum::name).sorted().toList(),
                    session.getExpiresAt()
            );
        } catch (ApiException e) {
            return new AdminSessionStateResponse(false, null, null, List.of(), null);
        }
    }

    @Transactional
    public void logout() {
        AdminPrincipal principal = adminSecurityService.getCurrentPrincipal();
        AdminSession session = adminSessionRepository.findById(principal.sessionId())
                .orElseThrow(() -> ApiException.unauthorized("ADMIN_SESSION_REQUIRED", "관리자 세션이 필요합니다."));
        adminSessionTokenService.revoke(session);
        auditSafely("ADMIN_LOGOUT", "ADMIN_USER", session.getUser().getId().toString(), null, null, null);
    }

        @Transactional
        public AdminAuthSessionResponse refreshSession() {
        AdminPrincipal principal = adminSecurityService.getCurrentPrincipal();
        AdminSession session = adminSessionRepository.findById(principal.sessionId())
            .orElseThrow(() -> ApiException.unauthorized("ADMIN_SESSION_REQUIRED", "관리자 세션이 필요합니다."));
        if (session.getStage() != AdminSessionStage.AUTHENTICATED) {
            throw ApiException.forbidden("ADMIN_AUTH_STEP_INVALID", "현재 단계에서는 세션을 갱신할 수 없습니다.");
        }

        User admin = session.getUser();
        AdminSessionTokenService.IssuedSession issuedSession = adminSessionTokenService.issue(
            admin,
            AdminSessionStage.AUTHENTICATED,
            adminConsoleProperties.getSessionTtlSeconds()
        );
        adminSessionTokenService.revoke(session);
        auditSafely(
            "ADMIN_SESSION_REFRESHED",
            "ADMIN_USER",
            admin.getId().toString(),
            null,
            Map.of("previousSessionId", session.getId().toString()),
            null
        );
        return toSessionResponse(issuedSession.rawToken(), issuedSession.session(), adminPermissionService.getPermissions(admin));
        }

    @Transactional
    public AdminPasskeyOptionsResponse startPendingPasskeyAssertion(HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.MFA_PENDING);
        return startAssertion(session, session.getUser(), request);
    }

    @Transactional
    public AdminPasskeyOptionsResponse startPromoteStepUp(HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.AUTHENTICATED);
        return startAssertion(session, session.getUser(), request);
    }

    @Transactional
    public AdminAuthSessionResponse verifyPromoteStepUp(AdminPasskeyCredentialRequest requestBody, HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.AUTHENTICATED);
        AdminAuthChallenge challenge = getChallenge(session, requestBody.getChallengeId(), AdminAuthChallengeType.PASSKEY_ASSERTION);
        try {
            AdminWebAuthnService.AssertionFinishPayload result = adminWebAuthnService.finishAssertion(
                    challenge.getRequestJson(),
                    toJsonString(requestBody.getCredential()),
                    request
            );
            String requestRpId = adminWebAuthnService.resolveRpId(request);
            adminPasskeyRepository.findByCredentialId(result.credentialId()).ifPresent(passkey -> {
                if (passkey.getRpId() != null && !passkey.getRpId().isBlank()
                        && !passkey.getRpId().equalsIgnoreCase(requestRpId)) {
                    throw ApiException.forbidden("PASSKEY_RP_MISMATCH", "현재 도메인과 일치하지 않는 PassKey입니다. 도메인별로 다시 등록해 주세요.");
                }
                passkey.setSignatureCount(result.signatureCount());
                passkey.setLastUsedAt(Instant.now());
                adminPasskeyRepository.save(passkey);
            });

            challenge.setUsedAt(Instant.now());
            session.setElevatedUntil(Instant.now().plusSeconds(adminConsoleProperties.getPromoteStepupTtlSeconds()));
            adminAuthChallengeRepository.save(challenge);
            adminSessionRepository.save(session);
            auditSafely("ADMIN_PROMOTE_STEPUP_VERIFIED", "ADMIN_USER", session.getUser().getId().toString(), null,
                    Map.of("elevatedUntil", session.getElevatedUntil()), null);
            return toSessionResponse(null, session, adminPermissionService.getPermissions(session.getUser()));
        } catch (Exception e) {
            String failureCode = mapPasskeyFailureCode(e);
            throw ApiException.forbidden(failureCode, "PassKey 승격 재인증에 실패했습니다.");
        }
    }

    @Transactional
    public AdminAuthSessionResponse resetPasskeys() {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.MFA_PENDING);
        User admin = session.getUser();
        long removedCount;
        try {
            removedCount = adminPasskeyRepository.deleteByUser(admin);
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_PASSKEY_RESET_FAILED", "관리자 PassKey 초기화에 실패했습니다.");
        }

        session.setStage(AdminSessionStage.PASSKEY_ENROLL);
        session.setExpiresAt(Instant.now().plusSeconds(adminConsoleProperties.getBootstrapSessionTtlSeconds()));
        adminSessionRepository.save(session);
        auditSafely(
                "ADMIN_PASSKEY_RESET",
                "ADMIN_USER",
                admin.getId().toString(),
                null,
                Map.of("removedCount", removedCount),
                Map.of("email", admin.getEmail())
        );
        return toSessionResponse(null, session, adminPermissionService.getPermissions(admin));
    }

    private AdminPasskeyOptionsResponse startAssertion(AdminSession session, User admin, HttpServletRequest request) {
        AdminPasskeyOptionsResponse reusable = reusePendingAssertionChallenge(session);
        if (reusable != null) {
            return reusable;
        }
        invalidatePendingChallenges(session, AdminAuthChallengeType.PASSKEY_ASSERTION);
        var assertion = adminWebAuthnService.startAssertion(admin, request);
        AdminAuthChallenge challenge = adminAuthChallengeRepository.save(AdminAuthChallenge.builder()
                .session(session)
                .user(admin)
                .type(AdminAuthChallengeType.PASSKEY_ASSERTION)
                .requestJson(serializeAssertion(assertion))
                .expiresAt(Instant.now().plusSeconds(adminConsoleProperties.getChallengeTtlSeconds()))
                .build());
        return new AdminPasskeyOptionsResponse(challenge.getId(), readJson(serializeAssertionOptions(assertion)));
    }

    private AdminPasskeyOptionsResponse reusePendingAssertionChallenge(AdminSession session) {
        Instant now = Instant.now();
        return adminAuthChallengeRepository.findBySessionAndTypeAndUsedAtIsNull(session, AdminAuthChallengeType.PASSKEY_ASSERTION)
                .stream()
                .filter(challenge -> challenge.getExpiresAt() != null && challenge.getExpiresAt().isAfter(now))
                .max(java.util.Comparator.comparing(AdminAuthChallenge::getCreatedAt))
                .map(challenge -> {
                    try {
                        var assertion = com.yubico.webauthn.AssertionRequest.fromJson(challenge.getRequestJson());
                        return new AdminPasskeyOptionsResponse(
                                challenge.getId(),
                                readJson(serializeAssertionOptions(assertion))
                        );
                    } catch (Exception e) {
                        challenge.setUsedAt(now);
                        adminAuthChallengeRepository.save(challenge);
                        return null;
                    }
                })
                .orElse(null);
    }

    private User resolveAdminUser(String firebaseUid, String email) {
        User admin = userRepository.findByFirebaseUid(firebaseUid)
                .or(() -> userRepository.findByEmail(email))
                .orElseThrow(() -> ApiException.forbidden("ADMIN_ONLY", "등록된 관리자 계정만 로그인할 수 있습니다."));

        if (admin.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("ADMIN_ONLY", "등록된 관리자 계정만 로그인할 수 있습니다.");
        }
        if (admin.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(
                    admin.getStatus() == UserStatus.BANNED ? "USER_SUSPENDED" : "USER_WITHDRAWN",
                    admin.getStatus() == UserStatus.BANNED ? "정지된 관리자 계정입니다." : "탈퇴한 관리자 계정입니다."
            );
        }
        admin.setFirebaseUid(firebaseUid);
        admin.setEmail(email);
        User saved = userRepository.save(admin);
        userRepository.flush();
        return saved;
    }

    private AdminLoginResponse sendInitialAdminEmailVerification(User admin, String firebaseUid, String firebaseIdToken, HttpServletRequest request) {
        resetFirebaseEmailVerified(firebaseUid);
        adminEmailVerificationService.sendVerificationEmail(firebaseIdToken, request);
        admin.setAdminEmailVerificationRequired(true);
        admin.setAdminEmailVerifiedAt(null);
        admin.setAdminEmailVerificationSentAt(Instant.now());
        userRepository.save(admin);
        auditSafely("ADMIN_EMAIL_VERIFICATION_SENT", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
        return new AdminLoginResponse(
                "EMAIL_VERIFICATION_REQUIRED",
                false,
                null,
                null
        );
    }

    private User syncAdminProfileIfMissing(User admin, FirebaseIdentityService.FirebaseIdentity identity, String firebaseUid) {
        String currentNickname = admin.getNickname();
        String currentPhoto = admin.getProfileImageUrl();
        boolean needsPhoto = currentPhoto == null || currentPhoto.isBlank();

        String displayName = identity != null ? identity.displayName() : null;
        String photoUrl = identity != null ? identity.photoUrl() : null;

        if ((displayName == null || displayName.isBlank()) || (photoUrl == null || photoUrl.isBlank())) {
            try {
                UserRecord firebaseUser = firebaseAuth.getUser(firebaseUid);
                if (firebaseUser != null) {
                    if (displayName == null || displayName.isBlank()) {
                        displayName = firebaseUser.getDisplayName();
                    }
                    if (photoUrl == null || photoUrl.isBlank()) {
                        photoUrl = firebaseUser.getPhotoUrl();
                    }
                }
            } catch (FirebaseAuthException e) {
                log.warn("관리자 프로필 동기화 조회 실패. userId={}, firebaseUid={}, reason={}",
                        admin.getId(),
                        firebaseUid,
                        e.getMessage());
            }
        }

        boolean changed = false;
        // Always update nickname if Firebase provider gives a displayName different from current.
        if (displayName != null && !displayName.isBlank()) {
            String trimmed = FirebaseDisplayNameNormalizer.normalize(displayName);
            if (!trimmed.equals(currentNickname)) {
                admin.setNickname(trimmed);
                changed = true;
            }
        }
        if (needsPhoto && photoUrl != null && !photoUrl.isBlank()) {
            admin.setProfileImageUrl(photoUrl.trim());
            changed = true;
        }
        if (!changed) {
            return admin;
        }
        return userRepository.save(admin);
    }

    private void syncExternalProviders(User user, FirebaseIdentityService.FirebaseIdentity identity) {
        List<String> providers = FirebaseProviderNormalizer.resolveLinkedProviders(
                identity,
                identity != null ? identity.signInProvider() : FirebaseProviderNormalizer.FIREBASE
        );
        for (String provider : providers) {
            if (!FirebaseProviderNormalizer.isExternalProvider(provider)) {
                continue;
            }
            FirebaseIdentityService.ProviderIdentity providerIdentity = findProviderIdentity(identity, provider);
            upsertSocialAccount(
                    user,
                    provider,
                    providerIdentity != null && providerIdentity.uid() != null ? providerIdentity.uid() : user.getFirebaseUid(),
                    providerIdentity != null && providerIdentity.email() != null ? providerIdentity.email() : user.getEmail()
            );
        }
    }

    private FirebaseIdentityService.ProviderIdentity findProviderIdentity(
            FirebaseIdentityService.FirebaseIdentity identity,
            String normalizedProvider
    ) {
        if (identity == null || identity.providers() == null) {
            return null;
        }
        return identity.providers().stream()
                .filter(provider -> provider != null && provider.providerId() != null)
                .filter(provider -> FirebaseProviderNormalizer.normalize(provider.providerId()).equals(normalizedProvider))
                .findFirst()
                .orElse(null);
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

    private AdminSession requireCurrentSessionStage(AdminSessionStage stage) {
        AdminPrincipal principal = adminSecurityService.getCurrentPrincipal();
        AdminSession session = adminSessionRepository.findById(principal.sessionId())
                .orElseThrow(() -> ApiException.unauthorized("ADMIN_SESSION_REQUIRED", "관리자 세션이 필요합니다."));
        if (session.getStage() != stage) {
            throw ApiException.forbidden("ADMIN_AUTH_STEP_INVALID", "현재 단계에서는 이 요청을 처리할 수 없습니다.");
        }
        return session;
    }

    private AdminAuthChallenge getChallenge(AdminSession session, UUID challengeId, AdminAuthChallengeType type) {
        Instant now = Instant.now();
        return adminAuthChallengeRepository.findByIdAndSessionAndType(challengeId, session, type)
                .filter(challenge -> challenge.getUsedAt() == null)
                .filter(challenge -> challenge.getExpiresAt() != null && challenge.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.forbidden("PASSKEY_CHALLENGE_INVALID", "유효한 PassKey 챌린지를 찾을 수 없습니다."));
    }

    private AdminAuthSessionResponse toSessionResponse(String rawTokenOverride, AdminSession session, Set<AdminPermission> permissions) {
        String token = rawTokenOverride != null ? rawTokenOverride : resolveCurrentToken();
        return new AdminAuthSessionResponse(
                token,
                session.getExpiresAt(),
                session.getStage().name(),
                toAdminInfo(session.getUser()),
                permissions.stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList()
        );
    }

    private AdminAuthSessionAdminResponse toAdminInfo(User user) {
        return new AdminAuthSessionAdminResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getEmail(),
                user.getRole().name()
        );
    }

    private String serialize(String json) {
        return json;
    }

    private String serializeOptions(com.yubico.webauthn.data.PublicKeyCredentialCreationOptions options) {
        try {
            return serialize(options.toJson());
        } catch (Exception e) {
            throw ApiException.internal("PASSKEY_JSON_ERROR", "PassKey 등록 요청 직렬화에 실패했습니다.");
        }
    }

    private String serializeCreateOptions(com.yubico.webauthn.data.PublicKeyCredentialCreationOptions options) {
        try {
            return options.toCredentialsCreateJson();
        } catch (Exception e) {
            throw ApiException.internal("PASSKEY_JSON_ERROR", "PassKey 등록 옵션 직렬화에 실패했습니다.");
        }
    }

    private String serializeAssertion(com.yubico.webauthn.AssertionRequest assertionRequest) {
        try {
            return assertionRequest.toJson();
        } catch (Exception e) {
            throw ApiException.internal("PASSKEY_JSON_ERROR", "PassKey 인증 요청 직렬화에 실패했습니다.");
        }
    }

    private String serializeAssertionOptions(com.yubico.webauthn.AssertionRequest assertionRequest) {
        try {
            return assertionRequest.toCredentialsGetJson();
        } catch (Exception e) {
            throw ApiException.internal("PASSKEY_JSON_ERROR", "PassKey 인증 옵션 직렬화에 실패했습니다.");
        }
    }

    private boolean resolveEmailVerified(String firebaseUid, boolean fallback) {
        try {
            UserRecord userRecord = firebaseAuth.getUser(firebaseUid);
            return userRecord != null ? userRecord.isEmailVerified() : fallback;
        } catch (FirebaseAuthException e) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Firebase 관리자 사용자 확인에 실패했습니다.");
        }
    }

    private void resetFirebaseEmailVerified(String firebaseUid) {
        try {
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(firebaseUid).setEmailVerified(false));
        } catch (FirebaseAuthException e) {
            log.warn("관리자 이메일 재인증 준비 실패. 로그인 흐름은 계속 진행합니다. firebaseUid={}, reason={}",
                    firebaseUid,
                    e.getMessage());
        }
    }

    private Object readJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw ApiException.internal("PASSKEY_JSON_ERROR", "PassKey 옵션 직렬화에 실패했습니다.");
        }
    }

    private String toJsonString(Object value) {
        try {
            Object normalized = value;
            if (value instanceof Map<?, ?> rawMap) {
                Map<String, Object> credential = new LinkedHashMap<>();
                rawMap.forEach((k, v) -> credential.put(String.valueOf(k), v));
                Object ext = credential.get("clientExtensionResults");
                if (!(ext instanceof Map)) {
                    credential.put("clientExtensionResults", Map.of());
                }
                normalized = credential;
            }
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            throw ApiException.badRequest("PASSKEY_INVALID", "PassKey 요청 형식이 올바르지 않습니다.");
        }
    }

    private String resolveCurrentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object credentials = authentication.getCredentials();
        return credentials instanceof String token && !token.isBlank() ? token : null;
    }

    private AdminLoginResponse buildLoginResponse(User admin, HttpServletRequest request, boolean forcePasskeyEnroll) {
        try {
            adminPermissionService.ensureDefaults(admin);
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_PERMISSION_INIT_FAILED", "관리자 권한 초기화에 실패했습니다.");
        }

        Set<AdminPermission> permissions;
        try {
            permissions = adminPermissionService.getPermissions(admin);
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_PERMISSION_LOAD_FAILED", "관리자 권한 조회에 실패했습니다.");
        }

        boolean hasPasskey;
        try {
            if (forcePasskeyEnroll) {
                adminPasskeyRepository.deleteByUser(admin);
                hasPasskey = false;
            } else {
                hasPasskey = hasUsablePasskeyForCurrentRp(admin, request);
            }
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_PASSKEY_STATE_FAILED", "관리자 PassKey 상태 조회에 실패했습니다.");
        }

        if (!hasPasskey) {
            AdminSessionTokenService.IssuedSession issuedSession;
            try {
                issuedSession = adminSessionTokenService.issue(
                        admin,
                        AdminSessionStage.PASSKEY_ENROLL,
                        adminConsoleProperties.getBootstrapSessionTtlSeconds()
                );
            } catch (RuntimeException e) {
                throw ApiException.internal("ADMIN_SESSION_ISSUE_FAILED", "관리자 부트스트랩 세션 발급에 실패했습니다.");
            }
            auditSafely("ADMIN_LOGIN_PASSKEY_ENROLL_REQUIRED", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
            AdminAuthSessionResponse sessionResponse;
            try {
                sessionResponse = toSessionResponse(issuedSession.rawToken(), issuedSession.session(), permissions);
            } catch (RuntimeException e) {
                throw ApiException.internal("ADMIN_SESSION_RESPONSE_FAILED", "관리자 로그인 세션 응답 생성에 실패했습니다.");
            }
            return new AdminLoginResponse(
                    "PASSKEY_REGISTRATION_REQUIRED",
                    true,
                    sessionResponse,
                    null
            );
        }

        AdminSessionTokenService.IssuedSession issuedSession;
        try {
            issuedSession = adminSessionTokenService.issue(
                    admin,
                    AdminSessionStage.MFA_PENDING,
                    adminConsoleProperties.getBootstrapSessionTtlSeconds()
            );
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_SESSION_ISSUE_FAILED", "관리자 MFA 대기 세션 발급에 실패했습니다.");
        }
        AdminPasskeyOptionsResponse options;
        try {
            options = startAssertion(issuedSession.session(), admin, request);
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_PASSKEY_OPTIONS_FAILED", "관리자 PassKey 인증 옵션 생성에 실패했습니다.");
        }
        auditSafely("ADMIN_LOGIN_PASSKEY_REQUIRED", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
        AdminAuthSessionResponse sessionResponse;
        try {
            sessionResponse = toSessionResponse(issuedSession.rawToken(), issuedSession.session(), permissions);
        } catch (RuntimeException e) {
            throw ApiException.internal("ADMIN_SESSION_RESPONSE_FAILED", "관리자 로그인 세션 응답 생성에 실패했습니다.");
        }
        return new AdminLoginResponse(
                "PASSKEY_REQUIRED",
                true,
                sessionResponse,
                options
        );
    }

    private void auditSafely(String action, String targetType, String targetId, Object before, Object after, Map<String, Object> metadata) {
        try {
            adminAuditService.log(action, targetType, targetId, before, after, metadata);
        } catch (RuntimeException e) {
            log.warn("Admin audit ignored action={} targetId={} reason={}", action, targetId, e.getClass().getSimpleName());
        }
    }

    private boolean hasUsablePasskeyForCurrentRp(User admin, HttpServletRequest request) {
        String requestRpId = null;
        try {
            requestRpId = adminWebAuthnService.resolveRpId(request);
        } catch (RuntimeException ignored) {
            // If RP ID resolution fails unexpectedly at login, fall back to legacy behavior.
        }
        Collection<AdminPasskey> passkeys = adminPasskeyRepository.findByUserOrderByCreatedAtAsc(admin);
        if (requestRpId == null || requestRpId.isBlank()) {
            return !passkeys.isEmpty();
        }
        final String targetRpId = requestRpId;
        return passkeys.stream().anyMatch(passkey ->
                passkey.getRpId() != null
                        && !passkey.getRpId().isBlank()
                        && passkey.getRpId().equalsIgnoreCase(targetRpId)
        );
    }


    private void invalidatePendingChallenges(AdminSession session, AdminAuthChallengeType type) {
        List<AdminAuthChallenge> pending = adminAuthChallengeRepository.findBySessionAndTypeAndUsedAtIsNull(session, type);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Invalidating pending passkey challenges. sessionId={}, type={}, pendingCount={}",
                session.getId(),
                type,
                pending.size());
        Instant now = Instant.now();
        pending.forEach(challenge -> challenge.setUsedAt(now));
        adminAuthChallengeRepository.saveAll(pending);
    }

    private String mapPasskeyFailureCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            if (apiException.getCode() != null && !apiException.getCode().isBlank()) {
                return apiException.getCode();
            }
            return "PASSKEY_INVALID";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "PASSKEY_INVALID";
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("incorrect challenge")) {
            return "PASSKEY_CHALLENGE_INVALID";
        }
        if (normalized.contains("origin")) {
            return "PASSKEY_ORIGIN_MISMATCH";
        }
        if (normalized.contains("rp id") || normalized.contains("rpid")) {
            return "PASSKEY_RP_MISMATCH";
        }
        if (normalized.contains("timed out") || normalized.contains("expired")) {
            return "PASSKEY_EXPIRED_CHALLENGE";
        }
        return "PASSKEY_INVALID";
    }
}
