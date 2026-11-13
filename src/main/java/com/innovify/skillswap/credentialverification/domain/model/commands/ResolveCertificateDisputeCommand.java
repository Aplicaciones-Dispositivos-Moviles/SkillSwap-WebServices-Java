package com.innovify.skillswap.credentialverification.domain.model.commands;

/**
 * Resolve certificate dispute command.
 *
 * @param certificateId   the escalated certificate
 * @param isAuthentic     the verifier's decision: true to verify, false to reject
 * @param rejectionReason why it was rejected (optional, up to {@link #MAX_REASON_LENGTH} characters); it is
 *                        included in the push notification sent to the student
 */
public record ResolveCertificateDisputeCommand(int certificateId, boolean isAuthentic, String rejectionReason) {

    public static final int MAX_REASON_LENGTH = 500;

    /** A decision without a reason. */
    public ResolveCertificateDisputeCommand(int certificateId, boolean isAuthentic) {
        this(certificateId, isAuthentic, null);
    }
}
