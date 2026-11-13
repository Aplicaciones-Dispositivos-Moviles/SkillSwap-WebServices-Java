package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/**
 * The verifier scores the video of a final demonstration with the rubric.
 *
 * @param score    0 to 100; 70 or more approves it, less rejects it
 * @param feedback what the student should improve (up to 1000 characters); required when the score rejects it
 */
public record ReviewFinalDemonstrationResource(Integer score, String feedback) {
}
