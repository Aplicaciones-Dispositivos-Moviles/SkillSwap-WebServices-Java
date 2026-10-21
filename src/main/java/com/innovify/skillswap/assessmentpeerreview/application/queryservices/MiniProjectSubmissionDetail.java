package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;

/**
 * What the student reads about a delivery: the delivery with its preliminary evaluation, and the case it opened.
 *
 * @param submission       the delivery
 * @param verificationCase the case opened for a verifier; null when none was opened (pending, approved by the AI, or
 *                         without escalations left)
 */
public record MiniProjectSubmissionDetail(MiniProjectSubmission submission, VerificationCase verificationCase) {
}
