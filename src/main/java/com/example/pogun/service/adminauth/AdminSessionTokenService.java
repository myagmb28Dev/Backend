package com.example.pogun.service.adminauth;

import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminSessionStage;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.admin.AdminSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminSessionTokenService {

    private static final long TOUCH_THROTTLE_SECONDS = 30L;

    private final AdminSessionRepository adminSessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedSession issue(User user, AdminSessionStage stage, long ttlSeconds) {
        String token = randomToken();
        AdminSession session = adminSessionRepository.save(AdminSession.builder()
                .user(user)
                .tokenHash(hash(token))
                .stage(stage)
                .expiresAt(Instant.now().plusSeconds(ttlSeconds))
                .lastUsedAt(Instant.now())
                .build());
        adminSessionRepository.flush();
        return new IssuedSession(token, session);
    }

    @Transactional(readOnly = true)
    public Optional<AdminSession> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return adminSessionRepository.findByTokenHash(hash(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt() != null && session.getExpiresAt().isAfter(Instant.now()));
    }

    @Transactional
    public void touch(AdminSession session) {
        if (session == null) {
            return;
        }
        Instant now = Instant.now();
        if (session.getLastUsedAt() != null
                && session.getLastUsedAt().isAfter(now.minusSeconds(TOUCH_THROTTLE_SECONDS))) {
            return;
        }
        session.setLastUsedAt(now);
        adminSessionRepository.save(session);
    }

    @Transactional
    public void revoke(AdminSession session) {
        if (session == null || session.getRevokedAt() != null) {
            return;
        }
        session.setRevokedAt(Instant.now());
        adminSessionRepository.save(session);
    }

    @Transactional
    public void revokeAll(User user) {
        adminSessionRepository.findByUserAndRevokedAtIsNullAndExpiresAtAfter(user, Instant.now())
                .forEach(this::revoke);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("관리자 세션 토큰 해시에 실패했습니다.", e);
        }
    }

    public record IssuedSession(String rawToken, AdminSession session) {
    }
}
