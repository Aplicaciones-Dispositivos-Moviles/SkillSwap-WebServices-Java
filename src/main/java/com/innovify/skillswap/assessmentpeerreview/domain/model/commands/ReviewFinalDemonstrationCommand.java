package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * The assigned verifier scores the video of a final demonstration with the rubric.
 *
 * @param demonstrationId the demonstration
 * @param verifierUserId  the verifier (the authenticated user)
 * @param score           0 to 100; 70 or more approves it
 * @param feedback        what the student should improve; required when the score rejects it (up to 1000 characters)
 */
public record ReviewFinalDemonstrationCommand(int demonstrationId, int verifierUserId, Integer score,
                                              String feedback) {
}
