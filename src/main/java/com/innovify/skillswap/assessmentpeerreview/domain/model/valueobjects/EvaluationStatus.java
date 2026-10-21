package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

/**
 * Where the delivery of a mini-project stands after its preliminary evaluation by the AI (US19).
 *
 * <ul>
 *   <li>{@link #PENDING}: delivered; the AI is still evaluating it.</li>
 *   <li>{@link #APPROVED}: the AI approved it with enough confidence, so the node was completed without a verifier.</li>
 *   <li>{@link #ESCALATED}: the evaluation did not reach the minimum, had a low confidence or could not be done, so a
 *       verification case was opened for a verifier.</li>
 *   <li>{@link #ESCALATION_LIMIT_REACHED}: it needed a verifier, but the student had used all the monthly
 *       escalations of the plan, so no case was opened.</li>
 * </ul>
 */
public enum EvaluationStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    ESCALATED("Escalated"),
    ESCALATION_LIMIT_REACHED("EscalationLimitReached");

    private final String value;

    EvaluationStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static EvaluationStatus fromValue(String value) {
        for (EvaluationStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown EvaluationStatus: " + value);
    }
}
