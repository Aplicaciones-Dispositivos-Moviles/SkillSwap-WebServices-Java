package com.innovify.skillswap.iam.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/** Encrypted representation of a password. The plain-text value never travels beyond the infrastructure layer. */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("The password hash cannot be empty.");
        }
    }

    // Never print the hash (logs, exceptions).
    @Override
    public String toString() {
        return "PasswordHash[****]";
    }
}
