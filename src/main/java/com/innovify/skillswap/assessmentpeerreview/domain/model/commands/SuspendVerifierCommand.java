package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A Verificador senior suspends a verifier with a low reliability.
 *
 * @param seniorUserId   the senior who suspends (the authenticated user)
 * @param verifierUserId the verifier to suspend
 * @param justification why (required, up to 2000 characters)
 */
public record SuspendVerifierCommand(int seniorUserId, int verifierUserId, String justification) {
}
