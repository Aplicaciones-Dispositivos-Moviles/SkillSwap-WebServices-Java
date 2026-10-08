package com.innovify.skillswap.credentialverification.domain.model.commands;

/**
 * Resolve certificate dispute command.
 *
 * @param certificateId the escalated certificate
 * @param isAuthentic   the Coordinator's decision: true to verify, false to reject
 */
public record ResolveCertificateDisputeCommand(int certificateId, boolean isAuthentic) {
}
