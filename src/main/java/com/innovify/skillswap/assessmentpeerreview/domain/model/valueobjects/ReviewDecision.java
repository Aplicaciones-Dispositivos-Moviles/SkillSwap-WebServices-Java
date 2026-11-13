package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

import java.util.Optional;

/** Decision a verifier records when resolving a case. */
public enum ReviewDecision {
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String value;

    ReviewDecision(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static ReviewDecision fromValue(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("Unknown ReviewDecision: " + value));
    }

    /** Case-insensitive lookup that answers empty for anything that is not a decision. */
    public static Optional<ReviewDecision> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (ReviewDecision candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.strip())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
