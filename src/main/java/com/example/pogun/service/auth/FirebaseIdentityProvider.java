package com.example.pogun.service.auth;

import com.google.firebase.auth.FirebaseAuthException;

public interface FirebaseIdentityProvider {

    FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) throws FirebaseAuthException;
}
