package com.example.pogun.service.auth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class CompositeFirebaseIdentityProvider implements FirebaseIdentityProvider {

    private final FirebaseIdentityProvider productionIdentityProvider;
    private final FirebaseIdentityProvider emulatorIdentityProvider;
    private final FirebaseIdentityProvider lookupIdentityProvider;
    private final FirebaseAuthProperties firebaseAuthProperties;
    private final ObjectMapper objectMapper;

    public CompositeFirebaseIdentityProvider(
            FirebaseIdentityProvider productionIdentityProvider,
            FirebaseIdentityProvider emulatorIdentityProvider,
            FirebaseIdentityProvider lookupIdentityProvider,
            FirebaseAuthProperties firebaseAuthProperties,
            ObjectMapper objectMapper
    ) {
        this.productionIdentityProvider = productionIdentityProvider;
        this.emulatorIdentityProvider = emulatorIdentityProvider;
        this.lookupIdentityProvider = lookupIdentityProvider;
        this.firebaseAuthProperties = firebaseAuthProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        if (looksLikeEmulatorToken(idToken)) {
            return emulatorIdentityProvider.verifyIdToken(idToken, checkRevoked);
        }
        try {
            return productionIdentityProvider.verifyIdToken(idToken, checkRevoked);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return lookupIdentityProvider.verifyIdToken(idToken, checkRevoked);
            }
            throw e;
        }
    }

    private boolean looksLikeEmulatorToken(String idToken) {
        String emulatorProjectId = blankToNull(firebaseAuthProperties.getEmulatorProjectId());
        if (emulatorProjectId == null) {
            return false;
        }

        String[] segments = idToken.split("\\.");
        if (segments.length < 2) {
            return false;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segments[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {
            });
            String aud = blankToNull(stringValue(payload.get("aud")));
            String iss = blankToNull(stringValue(payload.get("iss")));
            return emulatorProjectId.equals(aud)
                    && ("https://securetoken.google.com/" + emulatorProjectId).equals(iss);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        return new String(String.valueOf(value).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
