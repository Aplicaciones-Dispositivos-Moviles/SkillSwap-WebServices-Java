package com.innovify.skillswap.iam.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.Locale;

/** Unique text identifier used to access the system. Stored in lowercase. */
public record Username(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 100;

    public Username {
        if (!isValid(value)) {
            throw new DomainException("Username must be %d-%d characters long and contain no whitespace."
                    .formatted(MIN_LENGTH, MAX_LENGTH));
        }
        value = value.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String stripped = value.strip();
        return stripped.length() >= MIN_LENGTH
                && stripped.length() <= MAX_LENGTH
                && stripped.codePoints().noneMatch(Username::isWhitespace);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    @Override
    public String toString() {
        return value;
    }
}
