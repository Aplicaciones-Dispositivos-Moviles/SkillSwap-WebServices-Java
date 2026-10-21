package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * What the AI concluded about the delivery of a mini-project before any verifier sees it (US19 escenario 2): the
 * score it gave against the rubric, how sure it is of that score and a short feedback for the student. Immutable.
 *
 * <p>US19 escenario 3 sends the delivery to a verifier when the score does not reach the minimum of the rubric or the
 * confidence is low. The report does not fix a number for "low", so {@link #MIN_CONFIDENCE} sets it: below 70 out of
 * 100 the AI is not trusted to approve on its own.
 *
 * @param score      the score against the rubric, from 0 to {@value #MAX_SCORE}
 * @param confidence how sure the AI is of the score, from 0 to {@value #MAX_CONFIDENCE}
 * @param feedback   a short explanation for the student (at most {@value #MAX_FEEDBACK_LENGTH} characters)
 */
public record PreliminaryEvaluation(int score, int confidence, String feedback) {

    public static final int MAX_SCORE = 100;
    public static final int MAX_CONFIDENCE = 100;
    /** Below this confidence the evaluation cannot approve the delivery, whatever its score. */
    public static final int MIN_CONFIDENCE = 70;
    public static final int MAX_FEEDBACK_LENGTH = 1000;

    /**
     * @throws DomainException when the score or the confidence are out of range, or the feedback is too long
     */
    public PreliminaryEvaluation {
        if (score < 0 || score > MAX_SCORE) {
            throw new DomainException("The score of the preliminary evaluation must be between 0 and %d."
                    .formatted(MAX_SCORE));
        }
        if (confidence < 0 || confidence > MAX_CONFIDENCE) {
            throw new DomainException("The confidence of the preliminary evaluation must be between 0 and %d."
                    .formatted(MAX_CONFIDENCE));
        }
        feedback = feedback == null ? "" : feedback.strip();
        if (feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw new DomainException("The feedback of the preliminary evaluation cannot exceed %d characters."
                    .formatted(MAX_FEEDBACK_LENGTH));
        }
    }

    /** Whether the confidence is too low for the evaluation to decide on its own. */
    public boolean hasLowConfidence() {
        return confidence < MIN_CONFIDENCE;
    }

    /**
     * Whether the delivery can be approved without a verifier: the score reaches the minimum of the rubric and the
     * confidence is not low.
     */
    public boolean isSufficientFor(int passingScore) {
        return score >= passingScore && !hasLowConfidence();
    }
}
