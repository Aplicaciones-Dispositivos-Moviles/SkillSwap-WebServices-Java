package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/** A student becomes a verifier of a skill they completed, or enables one more skill. */
public record CreateVerifierProfileCommand(int userId, String skillTag) {
}
