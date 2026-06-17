package com.example.pogun.config.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTrafficLogbookConfigTest {

    @Test
    void sanitizeBody_redactsSensitiveJsonFields() {
        String sanitized = AdminTrafficLogbookConfig.sanitizeBody("""
                {
                  "firebaseIdToken": "secret-id-token",
                  "refreshToken": "secret-refresh-token",
                  "nested": {
                    "token": "nested-token"
                  }
                }
                """, "application/json");

        assertThat(sanitized).contains("\"firebaseIdToken\":\"[REDACTED]\"");
        assertThat(sanitized).contains("\"refreshToken\":\"[REDACTED]\"");
        assertThat(sanitized).contains("\"token\":\"[REDACTED]\"");
        assertThat(sanitized).doesNotContain("secret-id-token", "secret-refresh-token", "nested-token");
    }

    @Test
    void sanitizeBody_redactsSensitiveFormFields() {
        String sanitized = AdminTrafficLogbookConfig.sanitizeBody(
                "grant_type=refresh_token&refresh_token=secret-refresh-token&password=swordfish",
                "application/x-www-form-urlencoded"
        );

        assertThat(sanitized).isEqualTo("grant_type=refresh_token&refresh_token=[REDACTED]&password=[REDACTED]");
    }

    @Test
    void sanitizePath_redactsSensitiveQueryParameters() {
        String sanitized = AdminTrafficLogbookConfig.sanitizePath(
                "/api/auth/refresh?refresh_token=secret-refresh-token&token=abc123&ServiceKey=public-api-secret&PurchaseToken=purchase-secret&safe=value"
        );

        assertThat(sanitized).isEqualTo("/api/auth/refresh?refresh_token=%5BREDACTED%5D&token=%5BREDACTED%5D&ServiceKey=%5BREDACTED%5D&PurchaseToken=%5BREDACTED%5D&safe=value");
    }

    @Test
    void sanitizePath_redactsAbsoluteUrlWithoutDoubleEncoding() {
        String sanitized = AdminTrafficLogbookConfig.sanitizePath(
                "https://securetoken.googleapis.com/v1/token?key=firebase-secret&safe=value"
        );

        assertThat(sanitized).isEqualTo("https://securetoken.googleapis.com/v1/token?key=%5BREDACTED%5D&safe=value");
    }
}
