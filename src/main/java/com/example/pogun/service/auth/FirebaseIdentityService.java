package com.example.pogun.service.auth;

import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirebaseIdentityService {

    private final FirebaseIdentityProvider firebaseIdentityProvider;

    public FirebaseIdentity verifyIdToken(String idToken) throws FirebaseAuthException {
        return verifyIdToken(idToken, false);
    }

    public FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        return firebaseIdentityProvider.verifyIdToken(idToken, checkRevoked);
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
