package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/** A verifier switches their availability on or off. */
public record UpdateVerifierAvailabilityCommand(int userId, boolean available) {
}
