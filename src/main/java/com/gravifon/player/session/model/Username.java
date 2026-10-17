package com.gravifon.player.session.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record Username(String normalizedName, String displayName) {
    private static final Pattern ALLOWED_NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public Username {
        if (normalizedName == null || displayName == null) {
            throw new IllegalArgumentException("Username values are required");
        }
        if (!ALLOWED_NAME.matcher(displayName).matches()
                || !displayName.toLowerCase(Locale.ROOT).equals(normalizedName)) {
            throw new IllegalArgumentException("Username must be a normalized 1-64 character ASCII name");
        }
    }

    public static Username from(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Username is required");
        }
        String displayName = input.strip();
        if (!ALLOWED_NAME.matcher(displayName).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 1-64 ASCII letters, digits, dots, underscores, or hyphens"
            );
        }
        return new Username(displayName.toLowerCase(Locale.ROOT), displayName);
    }
}
