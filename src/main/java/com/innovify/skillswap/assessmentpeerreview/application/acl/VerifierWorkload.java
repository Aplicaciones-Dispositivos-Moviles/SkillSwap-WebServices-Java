package com.innovify.skillswap.assessmentpeerreview.application.acl;

/**
 * An enabled verifier who is available to take work, with the verification cases they have not resolved yet.
 *
 * @param verifierUserId the verifier
 * @param openCaseCount  their unresolved verification cases
 */
public record VerifierWorkload(int verifierUserId, int openCaseCount) {
}
