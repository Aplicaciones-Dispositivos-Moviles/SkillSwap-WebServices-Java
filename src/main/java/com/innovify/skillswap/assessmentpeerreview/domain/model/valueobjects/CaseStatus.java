package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/** Lifecycle states of a verification case. */
public enum CaseStatus {
    PENDING("Pending"),
    ASSIGNED("Assigned"),
    RESOLVED("Resolved");

    private final String value;

    CaseStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static CaseStatus fromValue(String value) {
        for (CaseStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown CaseStatus: " + value);
    }
}
