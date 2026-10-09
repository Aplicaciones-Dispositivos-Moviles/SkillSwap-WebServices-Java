package com.innovify.skillswap.iam.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Opaque identifier of the user's mobile device for push notifications: the Firebase Cloud Messaging registration
 * token the app receives once the student grants the notification permission.
 */
public record DeviceToken(String value) {

    public static final int MAX_LENGTH = 512;

    public DeviceToken {
        if (!isValid(value)) {
            throw new DomainException("The device token cannot be empty, contain whitespace or exceed %d characters."
                    .formatted(MAX_LENGTH));
        }
        value = value.strip();
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String stripped = value.strip();
        return stripped.length() <= MAX_LENGTH && stripped.codePoints().noneMatch(Character::isWhitespace);
    }

    /** Only the first characters, so the logs can tell devices apart without storing the token. */
    public String abbreviated() {
        return value.length() <= 8 ? "***" : value.substring(0, 8) + "...";
    }

    @Override
    public String toString() {
        return "DeviceToken[" + abbreviated() + "]";
    }
}
