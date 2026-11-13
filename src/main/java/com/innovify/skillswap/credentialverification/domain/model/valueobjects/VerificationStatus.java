package com.innovify.skillswap.credentialverification.domain.model.valueobjects;

/** Lifecycle states of a certificate within the verification flow. */
public enum VerificationStatus {
    PENDING("Pending"),
    UNVERIFIED("Unverified"),
    SUSPICIOUS("Suspicious"),
    VERIFIED("Verified"),
    REJECTED("Rejected");

    private final String value;

    VerificationStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static VerificationStatus fromValue(String value) {
        for (VerificationStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown VerificationStatus: " + value);
    }
}
