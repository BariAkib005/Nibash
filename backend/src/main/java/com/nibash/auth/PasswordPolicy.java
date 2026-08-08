package com.nibash.auth;

import com.nibash.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Django's password validators, reproduced (spec §4.2): minimum length, not entirely numeric, not a
 * common password, and not too similar to the user's own email or name. Failures are joined into a
 * single {@code detail} message.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    /** A small stand-in for Django's 20k-entry common-password list. */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890",
            "qwerty123", "qwertyuiop", "abc12345", "iloveyou", "admin123", "welcome1",
            "letmein1", "football", "baseball", "sunshine", "princess", "dragon123",
            "passw0rd", "trustno1", "changeme", "starwars", "whatever", "nibash123");

    public void validate(String password, String email, String name) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("This password is too short. It must contain at least " + MIN_LENGTH + " characters.");
        }
        if (password != null && password.chars().allMatch(Character::isDigit)) {
            errors.add("This password is entirely numeric.");
        }
        if (password != null && COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            errors.add("This password is too common.");
        }
        if (password != null && isSimilarToUser(password, email, name)) {
            errors.add("The password is too similar to the other personal information.");
        }

        if (!errors.isEmpty()) {
            throw ApiException.badRequest(String.join(" ", errors));
        }
    }

    /** Compares against the email local-part and each name token, as Django's validator does. */
    private boolean isSimilarToUser(String password, String email, String name) {
        String lower = password.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (email != null && !email.isBlank()) {
            candidates.add(email.toLowerCase(Locale.ROOT));
            candidates.add(email.split("@")[0].toLowerCase(Locale.ROOT));
        }
        if (name != null && !name.isBlank()) {
            for (String token : name.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (token.length() >= 3) {
                    candidates.add(token);
                }
            }
        }
        return candidates.stream()
                .filter(c -> c.length() >= 3)
                .anyMatch(c -> lower.contains(c) || c.contains(lower));
    }
}
