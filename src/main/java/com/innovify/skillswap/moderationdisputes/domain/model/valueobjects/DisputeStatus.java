package com.innovify.skillswap.moderationdisputes.domain.model.valueobjects;

import java.util.Optional;

/** Lifecycle states of a dispute. */
public enum DisputeStatus {
    PENDING("Pending"),
    RESOLVED("Resolved");

    private final String value;

    DisputeStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static DisputeStatus fromValue(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("Unknown DisputeStatus: " + value));
    }

    /** Case-insensitive lookup that answers empty for anything that is not a value of the enum. */
    public static Optional<DisputeStatus> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (DisputeStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.strip())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
