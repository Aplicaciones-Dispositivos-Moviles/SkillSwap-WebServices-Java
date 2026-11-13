package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;

/**
 * An attempt with what the student should reinforce.
 *
 * @param attempt      the graded attempt
 * @param weakSubTopic the sub-topic where the student failed the most; null when the wrong answers have none
 */
public record AssessmentAttemptDetail(AssessmentAttempt attempt, String weakSubTopic) {
}
