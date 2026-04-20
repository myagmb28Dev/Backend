package com.example.pogun.service.auth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class FirebaseEmulatorIdentityProvider implements FirebaseIdentityProvider {

    private final ObjectMapper objectMapper;
    private final FirebaseAuthProperties firebaseAuthProperties;
    private final FirebaseAuth firebaseAuth;

    @Override
    public FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) {
        if (!firebaseAuthProperties.isAllowEmulator()) {
            throw new IllegalArgumentException("Auth Emulator 토큰 사용이 허용되지 않았습니다.");
        }

        String[] segments = idToken.split("\\.");
        if (segments.length < 2) {
            throw new IllegalArgumentException("에뮬레이터 ID 토큰 형식이 올바르지 않습니다.");
        }

        Map<String, Object> payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segments[1]);
            payload = objectMapper.readValue(decoded, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("에뮬레이터 ID 토큰을 해석할 수 없습니다.", e);
        }

        String projectId = blankToNull(firebaseAuthProperties.getProjectId());
        if (projectId == null) {
            throw new IllegalArgumentException("Auth Emulator 사용 시 app.firebase.auth.project-id 또는 FIREBASE_PROJECT_ID가 필요합니다.");
        }

        String audience = stringValue(payload.get("aud"));
        if (!projectId.equals(audience)) {
            throw new IllegalArgumentException("에뮬레이터 토큰 프로젝트가 현재 프로젝트와 일치하지 않습니다.");
        }

        String issuer = stringValue(payload.get("iss"));
        String expectedIssuer = "https://securetoken.google.com/" + projectId;
        if (!expectedIssuer.equals(issuer)) {
            throw new IllegalArgumentException("에뮬레이터 토큰 issuer가 올바르지 않습니다.");
        }

        String uid = firstNonBlank(stringValue(payload.get("user_id")), stringValue(payload.get("sub")));
        if (uid == null) {
            throw new IllegalArgumentException("에뮬레이터 토큰에 사용자 식별자가 없습니다.");
        }

        if (checkRevoked) {
            verifyNotRevoked(uid, payload);
        }

        String email = stringValue(payload.get("email"));
        String displayName = stringValue(payload.get("name"));
        String photoUrl = stringValue(payload.get("picture"));
        String provider = extractProvider(payload.get("firebase"));

        List<FirebaseIdentityService.ProviderIdentity> providers = new ArrayList<>();
        if (provider != null) {
            providers.add(new FirebaseIdentityService.ProviderIdentity(provider, uid, email));
        }

        return new FirebaseIdentityService.FirebaseIdentity(uid, email, displayName, photoUrl, provider, providers, payload);
    }

    private void verifyNotRevoked(String uid, Map<String, Object> payload) {
        try {
            UserRecord userRecord = firebaseAuth.getUser(uid);
            long tokenValidAfterSeconds = userRecord.getTokensValidAfterTimestamp() / 1000L;
            Long issuedAtSeconds = asEpochSeconds(payload.get("iat"));
            if (issuedAtSeconds != null && issuedAtSeconds < tokenValidAfterSeconds) {
                throw new IllegalArgumentException("에뮬레이터 토큰이 무효화되었습니다.");
            }
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("에뮬레이터 토큰 사용자를 찾을 수 없습니다.", e);
        }
    }

    private Long asEpochSeconds(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractProvider(Object firebaseClaim) {
        if (firebaseClaim instanceof Map<?, ?> firebaseMap) {
            Object signInProvider = firebaseMap.get("sign_in_provider");
            return blankToNull(stringValue(signInProvider));
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        return String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
