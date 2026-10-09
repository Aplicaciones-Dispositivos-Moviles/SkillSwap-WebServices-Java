package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

/**
 * State of a learning path as a whole. A paused path keeps its progress, but the student cannot start new work on
 * it until it is resumed; the plan of the student limits how many paths are active at once.
 */
public enum PathStatus {
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed");

    private final String value;

    PathStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static PathStatus fromValue(String value) {
        for (PathStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown PathStatus: " + value);
    }
}
