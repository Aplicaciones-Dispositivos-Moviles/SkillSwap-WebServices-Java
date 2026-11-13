package com.innovify.skillswap.assessmentpeerreview.application.internal;

/**
 * The verifier chosen for a final demonstration and the skill of the path they are enabled in.
 *
 * @param verifierUserId the verifier
 * @param skillTag       the skill of the path the verifier can review
 */
public record DemonstrationAssignment(int verifierUserId, String skillTag) {
}
