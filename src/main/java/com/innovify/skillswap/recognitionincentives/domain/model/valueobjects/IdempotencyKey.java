package com.innovify.skillswap.recognitionincentives.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.Optional;

/**
 * The identifier the client sends with one attempt of a redemption (the Idempotency-Key header). Retrying the
 * attempt with the same key answers the movement already recorded instead of charging again. It is kept exactly as
 * sent: it cannot be blank and has at most {@value #MAX_LENGTH} characters.
 *
 * @param value the key
 */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 64;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new DomainException("The idempotency key cannot be blank.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(
                    "The idempotency key cannot exceed %d characters.".formatted(MAX_LENGTH));
        }
    }

    /** The key, or empty when the value is blank or too long. */
    public static Optional<IdempotencyKey> tryParse(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new IdempotencyKey(value));
    }
}
