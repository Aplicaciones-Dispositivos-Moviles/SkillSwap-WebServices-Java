package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;

/**
 * The result of the preliminary evaluation of a delivered mini-project.
 *
 * @param submission             the delivery, settled as Approved, Escalated or EscalationLimitReached
 * @param verificationCase       the case opened for a verifier; null when the AI approved the delivery or the monthly
 *                               escalations of the plan were used up
 * @param escalationLimitReached why a delivery that needed a verifier opened no case; null otherwise
 */
public record MiniProjectEvaluationOutcome(MiniProjectSubmission submission, VerificationCase verificationCase,
                                           EscalationLimitReached escalationLimitReached) {
}
