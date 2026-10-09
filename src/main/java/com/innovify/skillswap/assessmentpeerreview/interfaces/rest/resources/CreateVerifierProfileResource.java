package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/** The skill the student wants to be a verifier of; it must be a skill they completed. */
public record CreateVerifierProfileResource(String skillTag) {
}
