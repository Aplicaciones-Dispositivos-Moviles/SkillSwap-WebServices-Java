package com.innovify.skillswap.shared.domain.errors;

import java.util.Locale;

/**
 * Public code of a domain error: its constant name in PascalCase (USER_NOT_FOUND becomes "UserNotFound").
 * It is the title of the error responses and the key of the localized message.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static String of(Enum<?> error) {
        StringBuilder code = new StringBuilder();
        for (String word : error.name().split("_")) {
            if (!word.isEmpty()) {
                code.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return code.toString();
    }
}
