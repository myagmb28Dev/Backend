package com.example.pogun.service.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FirebaseProviderNormalizer {
    public static final String APPLE = "APPLE";
    public static final String FIREBASE = "FIREBASE";
    public static final String GOOGLE = "GOOGLE";

    private static final Set<String> ADMIN_LOGIN_PROVIDERS = Set.of(GOOGLE, APPLE);

    private FirebaseProviderNormalizer() {
    }

    public static String normalize(String providerId) {
        String resolved = providerId == null ? "firebase" : providerId.trim();
        if (resolved.isBlank()) {
            resolved = "firebase";
        }
        return switch (resolved.toLowerCase(Locale.ROOT)) {
            case "google.com" -> GOOGLE;
            case "apple.com" -> APPLE;
            case "facebook.com" -> "FACEBOOK";
            case "github.com" -> "GITHUB";
            case "phone" -> "PHONE";
            case "password", "email", "firebase" -> FIREBASE;
            case "google", "apple", "facebook", "github" -> resolved.toUpperCase(Locale.ROOT);
            default -> resolved.toUpperCase(Locale.ROOT).replace('.', '_');
        };
    }

    public static boolean isExternalProvider(String provider) {
        return !FIREBASE.equals(normalize(provider));
    }

    public static boolean isAdminLoginAllowed(FirebaseIdentityService.FirebaseIdentity identity) {
        return resolveLinkedProviders(identity, FIREBASE).stream().anyMatch(ADMIN_LOGIN_PROVIDERS::contains);
    }

    public static List<String> resolveLinkedProviders(FirebaseIdentityService.FirebaseIdentity identity, String fallbackProvider) {
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        if (identity != null && identity.providers() != null) {
            for (FirebaseIdentityService.ProviderIdentity provider : identity.providers()) {
                if (provider == null || provider.providerId() == null || provider.providerId().isBlank()) {
                    continue;
                }
                String normalized = normalize(provider.providerId());
                if (isExternalProvider(normalized)) {
                    providers.add(normalized);
                }
            }
        }

        if (providers.isEmpty()) {
            String normalizedFallback = normalize(fallbackProvider);
            providers.add(isExternalProvider(normalizedFallback) ? normalizedFallback : FIREBASE);
        }
        return List.copyOf(providers);
    }

    public static String resolvePrimaryProvider(FirebaseIdentityService.FirebaseIdentity identity, String fallbackProvider) {
        return resolveLinkedProviders(identity, fallbackProvider).stream()
                .findFirst()
                .orElseGet(() -> normalize(fallbackProvider));
    }
}
