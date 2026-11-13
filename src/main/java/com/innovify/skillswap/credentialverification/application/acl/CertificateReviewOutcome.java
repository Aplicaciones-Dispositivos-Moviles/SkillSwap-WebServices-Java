package com.innovify.skillswap.credentialverification.application.acl;

/** What happened when the decision of a verifier was applied to a suspicious certificate. */
public enum CertificateReviewOutcome {
    /** The certificate is now Verified or Rejected. */
    RESOLVED,
    /** There is no such certificate. */
    NOT_FOUND,
    /** The certificate is no longer suspicious: somebody resolved it already. */
    NOT_SUSPICIOUS,
    /** It could not be saved. */
    FAILED
}
