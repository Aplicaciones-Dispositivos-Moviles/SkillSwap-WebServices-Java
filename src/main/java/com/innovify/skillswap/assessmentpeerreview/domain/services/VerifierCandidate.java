package com.innovify.skillswap.assessmentpeerreview.domain.services;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;

/**
 * A verifier together with the number of cases they have not resolved yet.
 *
 * @param profile       the verifier
 * @param openCaseCount their unresolved cases
 */
public record VerifierCandidate(VerifierProfile profile, int openCaseCount) {
}
