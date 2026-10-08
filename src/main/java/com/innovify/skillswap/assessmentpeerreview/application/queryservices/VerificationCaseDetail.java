package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import java.util.List;

/** A verification case with the attempt that opened it and the questions that were failed. */
public record VerificationCaseDetail(VerificationCase verificationCase, AssessmentAttempt attempt,
                                     List<FailedQuestion> failedQuestions) {
}
