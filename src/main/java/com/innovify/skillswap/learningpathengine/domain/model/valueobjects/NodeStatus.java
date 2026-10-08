package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

/** State of a node inside a learning path. */
public enum NodeStatus {
    LOCKED("Locked"),
    AVAILABLE("Available"),
    COMPLETED("Completed");

    private final String value;

    NodeStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static NodeStatus fromValue(String value) {
        for (NodeStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown NodeStatus: " + value);
    }
}
