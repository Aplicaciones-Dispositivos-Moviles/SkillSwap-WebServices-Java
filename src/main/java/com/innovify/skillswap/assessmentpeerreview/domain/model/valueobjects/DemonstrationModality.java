package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/**
 * How the student gives the final demonstration of a completed path (US28).
 *
 * <ul>
 *   <li>{@link #ADVANCED_PROJECT}: the student shows an advanced project that applies the skills of the path.</li>
 *   <li>{@link #SUPERVISED_EXAM}: the student solves an exam supervised by the verifier.</li>
 * </ul>
 */
public enum DemonstrationModality {
    ADVANCED_PROJECT("AdvancedProject"),
    SUPERVISED_EXAM("SupervisedExam");

    private final String value;

    DemonstrationModality(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static DemonstrationModality fromValue(String value) {
        for (DemonstrationModality candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown DemonstrationModality: " + value);
    }
}
