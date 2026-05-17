package com.example.pogun.service.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseProviderNormalizerTest {

    @Test
    void resolveLinkedProviders_ignoresEmailWhenGoogleProviderExists() {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "uid",
                "user@example.com",
                "User",
                null,
                "google.com",
                List.of(
                        new FirebaseIdentityService.ProviderIdentity("google.com", "google-uid", "user@example.com"),
                        new FirebaseIdentityService.ProviderIdentity("password", "email-uid", "user@example.com")
                ),
                Map.of()
        );

        assertThat(FirebaseProviderNormalizer.resolveLinkedProviders(identity, "firebase"))
                .containsExactly("GOOGLE");
    }

    @Test
    void resolveLinkedProviders_acceptsAppleAsSocialProvider() {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "uid",
                "user@privaterelay.appleid.com",
                "User",
                null,
                "apple.com",
                List.of(
                        new FirebaseIdentityService.ProviderIdentity("apple.com", "apple-sub", "user@privaterelay.appleid.com"),
                        new FirebaseIdentityService.ProviderIdentity("password", "email-uid", "user@privaterelay.appleid.com")
                ),
                Map.of()
        );

        assertThat(FirebaseProviderNormalizer.resolvePrimaryProvider(identity, "firebase"))
                .isEqualTo("APPLE");
        assertThat(FirebaseProviderNormalizer.resolveLinkedProviders(identity, "firebase"))
                .containsExactly("APPLE");
        assertThat(FirebaseProviderNormalizer.isAdminLoginAllowed(identity)).isTrue();
    }

    @Test
    void isAdminLoginAllowed_rejectsEmailOnlyProvider() {
        FirebaseIdentityService.FirebaseIdentity identity = new FirebaseIdentityService.FirebaseIdentity(
                "uid",
                "user@example.com",
                "User",
                null,
                "password",
                List.of(new FirebaseIdentityService.ProviderIdentity("password", "email-uid", "user@example.com")),
                Map.of()
        );

        assertThat(FirebaseProviderNormalizer.resolveLinkedProviders(identity, "firebase"))
                .containsExactly("FIREBASE");
        assertThat(FirebaseProviderNormalizer.isAdminLoginAllowed(identity)).isFalse();
    }
}
