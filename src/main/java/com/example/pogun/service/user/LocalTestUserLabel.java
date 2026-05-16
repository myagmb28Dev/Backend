package com.example.pogun.service.user;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalTestUserLabel {

    private static final Pattern LOCAL_TEST_EMAIL_PATTERN = Pattern.compile(
            "^(?:dm-user|playwright-user)(\\d{1,2})(?:[-_].*)?@local\\.dev$",
            Pattern.CASE_INSENSITIVE
    );

    private LocalTestUserLabel() {
    }

    public static Optional<String> nicknameFromEmail(String email) {
        return numberFromEmail(email).map(number -> "유저" + number);
    }

    public static Optional<String> displayNameFromEmail(String email) {
        return numberFromEmail(email).map(number -> "유저 " + number);
    }

    private static Optional<Integer> numberFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = LOCAL_TEST_EMAIL_PATTERN.matcher(email.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        return number >= 1 && number <= 10 ? Optional.of(number) : Optional.empty();
    }
}
