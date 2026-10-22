package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A Verificador senior reinstates a suspended verifier.
 *
 * @param seniorUserId   the senior who reinstates (the authenticated user)
 * @param verifierUserId the verifier to reinstate
 */
public record ReinstateVerifierCommand(int seniorUserId, int verifierUserId) {
}
