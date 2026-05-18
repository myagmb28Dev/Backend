package com.example.pogun.service.auth;

import java.util.Arrays;
import java.util.regex.Pattern;

public final class FirebaseDisplayNameNormalizer {
    private static final Pattern HANGUL_TOKEN = Pattern.compile("\\p{IsHangul}+");

    private FirebaseDisplayNameNormalizer() {
    }

    public static String normalize(String displayName) {
        if (displayName == null) {
            return null;
        }
        String trimmed = displayName.trim().replaceAll("\\s+", " ");
        if (trimmed.isBlank()) {
            return trimmed;
        }

        String[] parts = trimmed.split(" ");
        if (parts.length < 2 || !Arrays.stream(parts).allMatch(FirebaseDisplayNameNormalizer::isHangul)) {
            return trimmed;
        }

        String first = parts[0];
        String last = parts[parts.length - 1];
        if (first.length() == 1) {
            return String.join("", parts);
        }
        if (last.length() == 1) {
            return last + String.join("", Arrays.copyOf(parts, parts.length - 1));
        }
        return trimmed;
    }

    private static boolean isHangul(String value) {
        return value != null && HANGUL_TOKEN.matcher(value).matches();
    }
}
