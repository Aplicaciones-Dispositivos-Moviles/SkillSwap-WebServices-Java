package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;

/**
 * The result of submitting an attempt.
 *
 * @param attempt                the graded attempt
 * @param verificationCase       the case opened when the attempt did not pass; null when it passed, or when the
 *                               monthly escalations of the plan were used up
 * @param escalationLimitReached why a failed attempt opened no case; null otherwise
 */
public record SubmitAssessmentAttemptOutcome(AssessmentAttempt attempt, VerificationCase verificationCase,
                                             EscalationLimitReached escalationLimitReached) {

    public SubmitAssessmentAttemptOutcome(AssessmentAttempt attempt, VerificationCase verificationCase) {
        this(attempt, verificationCase, null);
    }
}
