package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/**
 * Resource for suspending a verifier.
 *
 * @param justification why the verifier is suspended (required, up to 2000 characters)
 */
public record SuspendVerifierResource(String justification) {
}
