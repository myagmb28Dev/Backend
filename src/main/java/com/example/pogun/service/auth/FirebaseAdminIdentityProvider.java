package com.example.pogun.service.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class FirebaseAdminIdentityProvider implements FirebaseIdentityProvider {

    private final FirebaseAuth firebaseAuth;

    @Override
    public FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken, checkRevoked);
        String provider = extractProvider(decodedToken);
        List<FirebaseIdentityService.ProviderIdentity> providers = resolveProviders(decodedToken);
        String displayName = decodedToken.getName();
        String photoUrl = stringValue(decodedToken.getClaims().get("picture"));

        return new FirebaseIdentityService.FirebaseIdentity(
                decodedToken.getUid(),
                decodedToken.getEmail(),
                displayName,
                photoUrl,
                provider,
                providers,
                decodedToken.getClaims()
        );
    }

    private List<FirebaseIdentityService.ProviderIdentity> resolveProviders(FirebaseToken decodedToken) {
        List<FirebaseIdentityService.ProviderIdentity> providers = new ArrayList<>();

        Object firebaseClaim = decodedToken.getClaims().get("firebase");
        if (firebaseClaim instanceof Map<?, ?> firebaseMap) {
            Object identitiesObject = firebaseMap.get("identities");
            if (identitiesObject instanceof Map<?, ?> identities) {
                for (Map.Entry<?, ?> entry : identities.entrySet()) {
                    String providerId = stringValue(entry.getKey());
                    if (providerId == null || providerId.isBlank()) {
                        continue;
                    }
                    String providerUid = null;
                    String providerEmail = decodedToken.getEmail();
                    Object value = entry.getValue();
                    if (value instanceof List<?> list && !list.isEmpty()) {
                        Object first = list.get(0);
                        String firstValue = stringValue(first);
                        providerUid = firstValue;
                        if ("email".equalsIgnoreCase(providerId) || "password".equalsIgnoreCase(providerId)) {
                            providerEmail = firstValue;
                        }
                    }
                    providers.add(new FirebaseIdentityService.ProviderIdentity(providerId, providerUid, providerEmail));
                }
            }
        }

        if (providers.isEmpty()) {
            String provider = extractProvider(decodedToken);
            if (provider != null && !provider.isBlank()) {
                providers.add(new FirebaseIdentityService.ProviderIdentity(provider, decodedToken.getUid(), decodedToken.getEmail()));
            }
        }

        return providers;
    }

    @SuppressWarnings("unchecked")
    private String extractProvider(FirebaseToken decodedToken) {
        Object firebaseClaim = decodedToken.getClaims().get("firebase");
        if (firebaseClaim instanceof java.util.Map<?, ?> firebaseMap) {
            Object signInProvider = firebaseMap.get("sign_in_provider");
            if (signInProvider instanceof String provider && !provider.isBlank()) {
                return provider;
            }
        }
        return "FIREBASE";
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
}
