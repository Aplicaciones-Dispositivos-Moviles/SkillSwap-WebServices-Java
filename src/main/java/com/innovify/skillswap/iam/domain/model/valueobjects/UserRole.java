package com.innovify.skillswap.iam.domain.model.valueobjects;

/**
 * The role assigned to an account.
 *
 * <p>The Verifier profile is not a role: it is an additional profile (VerifierProfile) that a Student can
 * acquire, managed in the Assessment &amp; Peer Review bounded context.
 *
 * <p>{@link #value()} is the representation stored in the database and exposed by the API
 * ("Student", "Coordinator").
 */
public enum UserRole {
    STUDENT("Student"),
    COORDINATOR("Coordinator");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static UserRole fromValue(String value) {
        for (UserRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
