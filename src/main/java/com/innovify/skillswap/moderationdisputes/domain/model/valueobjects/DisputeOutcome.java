package com.innovify.skillswap.moderationdisputes.domain.model.valueobjects;

import java.util.Optional;

/** The decision of the reviewer. Which ones apply depends on the origin of the dispute (see DisputeResolutionValidator). */
public enum DisputeOutcome {
    /** The original stands: for a certificate review, the certificate is legitimate and becomes Verified. */
    UPHELD("Upheld"),
    /** The original is reversed: for a certificate review, the certificate is fraudulent and becomes Rejected. */
    OVERTURNED("Overturned"),
    /** A report that does not deserve a sanction. */
    DISMISSED("Dismissed"),
    /** A report that deserves a sanction. */
    SANCTIONED("Sanctioned");

    private final String value;

    DisputeOutcome(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static DisputeOutcome fromValue(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("Unknown DisputeOutcome: " + value));
    }

    /** Case-insensitive lookup that answers empty for anything that is not a value of the enum. */
    public static Optional<DisputeOutcome> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (DisputeOutcome candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.strip())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
