package com.innovify.skillswap.recognitionincentives.domain.model.valueobjects;

/** The kind of movement of a wallet. */
public enum TransactionType {
    /** Credits earned by resolving a verification case. */
    EARNED("Earned"),
    /** Credits spent on a benefit. */
    REDEEMED("Redeemed");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static TransactionType fromValue(String value) {
        for (TransactionType candidate : values()) {
            if (value != null && candidate.value.equalsIgnoreCase(value.strip())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown TransactionType: " + value);
    }
}
