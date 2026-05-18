package com.example.pogun.service.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseDisplayNameNormalizerTest {
    @Test
    void normalize_movesTrailingKoreanFamilyNameToFront() {
        assertThat(FirebaseDisplayNameNormalizer.normalize("준혁 장")).isEqualTo("장준혁");
    }

    @Test
    void normalize_joinsLeadingKoreanFamilyNameWithoutChangingOrder() {
        assertThat(FirebaseDisplayNameNormalizer.normalize("장 준혁")).isEqualTo("장준혁");
    }

    @Test
    void normalize_keepsNonKoreanDisplayNameOrder() {
        assertThat(FirebaseDisplayNameNormalizer.normalize("Apple User")).isEqualTo("Apple User");
    }
}
