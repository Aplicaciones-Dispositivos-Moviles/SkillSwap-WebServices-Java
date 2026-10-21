package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;

/**
 * The result of delivering a mini-project.
 *
 * @param submission             the saved delivery
 * @param verificationCase       the case opened by the delivery; null when the monthly escalations of the plan were
 *                               used up
 * @param escalationLimitReached why the delivery opened no case; null otherwise
 */
public record SubmitMiniProjectOutcome(MiniProjectSubmission submission, VerificationCase verificationCase,
                                       EscalationLimitReached escalationLimitReached) {
}
