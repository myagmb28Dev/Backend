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
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.admin.AdminAuthChallengeRepository;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.repository.admin.AdminSessionRepository;
import com.example.pogun.repository.user.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.example.pogun.service.auth.FirebaseIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuth firebaseAuth;
    private final FirebaseIdentityService firebaseIdentityService;
    private final UserRepository userRepository;
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

    public AdminLoginResponse loginWithGoogleToken(String firebaseIdToken, HttpServletRequest request) {
        FirebaseIdentityService.FirebaseIdentity identity;
        try {
            identity = firebaseIdentityService.verifyIdToken(firebaseIdToken, true);
        } catch (Exception e) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "유효한 Google 로그인 토큰이 필요합니다.");
        }

        if (!isGoogleSignIn(identity)) {
            throw ApiException.forbidden("GOOGLE_SIGN_IN_REQUIRED", "관리자 로그인은 Google 소셜 로그인만 허용됩니다.");
        }

        String firebaseUid = identity.uid();
        String email = identity.email();
        if (firebaseUid == null || firebaseUid.isBlank() || email == null || email.isBlank()) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Google 로그인 사용자 정보를 확인할 수 없습니다.");
        }

        User admin = resolveAdminUser(firebaseUid, email);
        boolean emailVerified = resolveEmailVerified(firebaseUid, false);

        if (admin.isAdminEmailVerificationRequired()) {
            if (emailVerified && admin.getAdminEmailVerifiedAt() == null) {
                resetFirebaseEmailVerified(firebaseUid);
                emailVerified = false;
            }
            if (!emailVerified) {
                adminEmailVerificationService.sendVerificationEmail(firebaseIdToken);
                adminAuditService.log("ADMIN_EMAIL_VERIFICATION_SENT", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
                return new AdminLoginResponse(
                        "EMAIL_VERIFICATION_REQUIRED",
                        false,
                        null,
                        null
                );
            }
            admin.setAdminEmailVerificationRequired(false);
            admin.setAdminEmailVerifiedAt(Instant.now());
            admin = userRepository.save(admin);
        } else if (!emailVerified) {
            adminEmailVerificationService.sendVerificationEmail(firebaseIdToken);
            adminAuditService.log("ADMIN_EMAIL_VERIFICATION_SENT", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
            return new AdminLoginResponse(
                    "EMAIL_VERIFICATION_REQUIRED",
                    false,
                    null,
                    null
            );
        }

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
            hasPasskey = adminPasskeyRepository.existsByUser(admin);
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
            adminAuditService.log("ADMIN_LOGIN_PASSKEY_ENROLL_REQUIRED", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
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
        adminAuditService.log("ADMIN_LOGIN_PASSKEY_REQUIRED", "ADMIN_USER", admin.getId().toString(), null, Map.of("email", admin.getEmail()), null);
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

    private boolean isGoogleSignIn(FirebaseIdentityService.FirebaseIdentity identity) {
        if (identity == null) {
            return false;
        }
        if ("google.com".equalsIgnoreCase(identity.signInProvider())) {
            return true;
        }
        return identity.providers() != null && identity.providers().stream()
                .map(FirebaseIdentityService.ProviderIdentity::providerId)
                .anyMatch(providerId -> providerId != null && providerId.equalsIgnoreCase("google.com"));
    }

    @Transactional
    public AdminPasskeyOptionsResponse beginPasskeyRegistration(HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.PASSKEY_ENROLL);
        User admin = session.getUser();
        var options = adminWebAuthnService.startRegistration(admin, request);
        AdminAuthChallenge challenge = adminAuthChallengeRepository.save(AdminAuthChallenge.builder()
                .session(session)
                .user(admin)
                .type(AdminAuthChallengeType.PASSKEY_REGISTRATION)
                .requestJson(serializeOptions(options))
                .expiresAt(Instant.now().plusSeconds(adminConsoleProperties.getChallengeTtlSeconds()))
                .build());
        return new AdminPasskeyOptionsResponse(challenge.getId(), readJson(serializeCreateOptions(options)));
    }

    @Transactional
    public AdminAuthSessionResponse finishPasskeyRegistration(AdminPasskeyCredentialRequest requestBody, HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.PASSKEY_ENROLL);
        AdminAuthChallenge challenge = getChallenge(session, requestBody.getChallengeId(), AdminAuthChallengeType.PASSKEY_REGISTRATION);
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
                    .lastUsedAt(Instant.now())
                    .build());
            challenge.setUsedAt(Instant.now());
            session.setStage(AdminSessionStage.AUTHENTICATED);
            session.setExpiresAt(Instant.now().plusSeconds(adminConsoleProperties.getSessionTtlSeconds()));
            adminAuthChallengeRepository.save(challenge);
            adminSessionRepository.save(session);
            adminAuditService.log("ADMIN_PASSKEY_REGISTERED", "ADMIN_USER", session.getUser().getId().toString(), null, Map.of("credentialId", result.credentialId()), null);
            return toSessionResponse(null, session, adminPermissionService.getPermissions(session.getUser()));
        } catch (Exception e) {
            throw ApiException.forbidden("PASSKEY_INVALID", "PassKey 등록 검증에 실패했습니다.");
        }
    }

    @Transactional
    public AdminAuthSessionResponse verifyMfa(AdminPasskeyCredentialRequest requestBody, HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.MFA_PENDING);
        AdminAuthChallenge challenge = getChallenge(session, requestBody.getChallengeId(), AdminAuthChallengeType.PASSKEY_ASSERTION);
        try {
            AdminWebAuthnService.AssertionFinishPayload result = adminWebAuthnService.finishAssertion(
                    challenge.getRequestJson(),
                    toJsonString(requestBody.getCredential()),
                    request
            );
            adminPasskeyRepository.findByCredentialId(result.credentialId()).ifPresent(passkey -> {
                passkey.setSignatureCount(result.signatureCount());
                passkey.setLastUsedAt(Instant.now());
                adminPasskeyRepository.save(passkey);
            });
            challenge.setUsedAt(Instant.now());
            session.setStage(AdminSessionStage.AUTHENTICATED);
            session.setExpiresAt(Instant.now().plusSeconds(adminConsoleProperties.getSessionTtlSeconds()));
            adminAuthChallengeRepository.save(challenge);
            adminSessionRepository.save(session);
            adminAuditService.log("ADMIN_LOGIN_AUTHENTICATED", "ADMIN_USER", session.getUser().getId().toString(), null, Map.of("email", session.getUser().getEmail()), null);
            return toSessionResponse(null, session, adminPermissionService.getPermissions(session.getUser()));
        } catch (Exception e) {
            throw ApiException.forbidden("PASSKEY_INVALID", "PassKey 검증에 실패했습니다.");
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
        adminAuditService.log("ADMIN_LOGOUT", "ADMIN_USER", session.getUser().getId().toString(), null, null, null);
    }

    @Transactional
    public AdminPasskeyOptionsResponse startPendingPasskeyAssertion(HttpServletRequest request) {
        AdminSession session = requireCurrentSessionStage(AdminSessionStage.MFA_PENDING);
        return startAssertion(session, session.getUser(), request);
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
        adminAuditService.log(
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
        return adminAuthChallengeRepository.findByIdAndSessionAndType(challengeId, session, type)
                .filter(challenge -> challenge.getUsedAt() == null)
                .filter(challenge -> challenge.getExpiresAt() != null && challenge.getExpiresAt().isAfter(Instant.now()))
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
            throw ApiException.internal("ADMIN_EMAIL_REVERIFY_PREPARE_FAILED", "관리자 이메일 재인증 준비에 실패했습니다.");
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
            return OBJECT_MAPPER.writeValueAsString(value);
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
}
