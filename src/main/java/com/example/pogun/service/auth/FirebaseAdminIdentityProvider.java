package com.example.pogun.service.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class FirebaseAdminIdentityProvider implements FirebaseIdentityProvider {

    private final FirebaseAuth firebaseAuth;

    @Override
    public FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken, checkRevoked);
        UserRecord userRecord = firebaseAuth.getUser(decodedToken.getUid());

        String provider = extractProvider(decodedToken);
        List<FirebaseIdentityService.ProviderIdentity> providers = new ArrayList<>();

        UserInfo[] providerData = userRecord.getProviderData();
        if (providerData != null) {
            for (UserInfo info : providerData) {
                String providerId = info.getProviderId();
                if (providerId == null || providerId.isBlank() || "firebase".equalsIgnoreCase(providerId)) {
                    continue;
                }
                providers.add(new FirebaseIdentityService.ProviderIdentity(providerId, info.getUid(), info.getEmail()));
            }
        }

        return new FirebaseIdentityService.FirebaseIdentity(
                decodedToken.getUid(),
                decodedToken.getEmail(),
                userRecord.getDisplayName(),
                userRecord.getPhotoUrl(),
                provider,
                providers,
                decodedToken.getClaims()
        );
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
}
