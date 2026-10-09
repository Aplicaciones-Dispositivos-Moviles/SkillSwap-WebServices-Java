package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;

/**
 * The result of submitting an attempt.
 *
 * @param attempt          the graded attempt
 * @param verificationCase the case opened when the attempt did not pass; null when it passed
 */
public record SubmitAssessmentAttemptOutcome(AssessmentAttempt attempt, VerificationCase verificationCase) {
}
