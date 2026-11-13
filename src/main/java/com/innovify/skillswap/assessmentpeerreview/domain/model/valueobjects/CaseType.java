package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/** What the student handed in for review: the answers of a quiz or a mini-project. */
public enum CaseType {
    QUIZ("Quiz"),
    MINI_PROJECT("MiniProject");

    private final String value;

    CaseType(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static CaseType fromValue(String value) {
        for (CaseType candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown CaseType: " + value);
    }
}
