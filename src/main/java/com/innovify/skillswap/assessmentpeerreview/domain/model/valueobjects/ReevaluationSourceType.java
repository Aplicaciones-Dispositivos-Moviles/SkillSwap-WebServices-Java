package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/** What a senior verifier ordered to evaluate again: an approved attempt or an approved verification case. */
public enum ReevaluationSourceType {
    ATTEMPT("Attempt"),
    CASE("Case");

    private final String value;

    ReevaluationSourceType(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static ReevaluationSourceType fromValue(String value) {
        for (ReevaluationSourceType candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ReevaluationSourceType: " + value);
    }
}
