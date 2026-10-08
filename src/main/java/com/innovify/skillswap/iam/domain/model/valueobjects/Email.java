package com.innovify.skillswap.iam.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Institutional email address. Only addresses from the .edu.pe domain are accepted. Stored in lowercase. */
public record Email(String value) {

    public static final String INSTITUTIONAL_SUFFIX = ".edu.pe";

    private static final Pattern PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.edu\\.pe$", Pattern.UNICODE_CHARACTER_CLASS);

    public Email {
        if (!isValid(value)) {
            throw new DomainException("The email must be a valid institutional (.edu.pe) email address.");
        }
        value = normalize(value);
    }

    public static boolean isValid(String value) {
        return value != null && !value.isBlank() && PATTERN.matcher(normalize(value)).matches();
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
