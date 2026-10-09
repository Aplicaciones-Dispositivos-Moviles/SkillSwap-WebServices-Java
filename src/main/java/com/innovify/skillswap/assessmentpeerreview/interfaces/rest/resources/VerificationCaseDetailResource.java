package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.util.List;

/**
 * What the student and the assigned verifier read about a case: the case, the attempt and the questions that
 * were failed.
 */
public record VerificationCaseDetailResource(VerificationCaseResource verificationCase,
                                             AssessmentAttemptResource attempt,
                                             List<FailedQuestionResource> failedQuestions) {
}
