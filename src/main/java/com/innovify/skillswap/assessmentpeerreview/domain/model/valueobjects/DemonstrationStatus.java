package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/**
 * Where a final demonstration stands (US28, US29).
 *
 * <ul>
 *   <li>{@link #SCHEDULED}: the student chose the modality and the moment and a verifier was assigned.</li>
 *   <li>{@link #VIDEO_SUBMITTED}: the student uploaded the video of the demonstration.</li>
 *   <li>{@link #APPROVED}: the verifier approved the demonstration with the rubric.</li>
 *   <li>{@link #REJECTED}: the verifier did not approve it; the student can schedule another one.</li>
 *   <li>{@link #CANCELLED}: the student cancelled it before the video was uploaded.</li>
 * </ul>
 */
public enum DemonstrationStatus {
    SCHEDULED("Scheduled"),
    VIDEO_SUBMITTED("VideoSubmitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String value;

    DemonstrationStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Whether the demonstration still blocks a new one for the same path (it is not rejected nor cancelled). */
    public boolean blocksNewDemonstration() {
        return this == SCHEDULED || this == VIDEO_SUBMITTED || this == APPROVED;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static DemonstrationStatus fromValue(String value) {
        for (DemonstrationStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown DemonstrationStatus: " + value);
    }
}
