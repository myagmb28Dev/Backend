package com.example.pogun.service.auth;

import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FirebaseIdentityService {

    private final FirebaseIdentityProvider firebaseIdentityProvider;
    private final ConcurrentHashMap<String, Instant> locallyRevokedTokens = new ConcurrentHashMap<>();

    public FirebaseIdentity verifyIdToken(String idToken) throws FirebaseAuthException {
        return verifyIdToken(idToken, false);
    }

    public FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        if (checkRevoked) {
            cleanupExpiredRevokedTokens();
            Instant revokedAt = locallyRevokedTokens.get(idToken);
            if (revokedAt != null && revokedAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("이미 무효화된 토큰입니다.");
            }
        }
        return firebaseIdentityProvider.verifyIdToken(idToken, checkRevoked);
    }

    public void revokeTokenLocally(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return;
        }
        // ID 토큰 만료(기본 1시간) 이후 자동 정리되도록 TTL을 함께 저장한다.
        locallyRevokedTokens.put(idToken, Instant.now().plusSeconds(3600));
        cleanupExpiredRevokedTokens();
    }

    private void cleanupExpiredRevokedTokens() {
        Instant now = Instant.now();
        locallyRevokedTokens.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(now));
    }

    public record FirebaseIdentity(
            String uid,
            String email,
            String displayName,
            String photoUrl,
            String signInProvider,
            List<ProviderIdentity> providers,
            Map<String, Object> claims
    ) {
    }

    public record ProviderIdentity(
            String providerId,
            String uid,
            String email
    ) {
    }
}
