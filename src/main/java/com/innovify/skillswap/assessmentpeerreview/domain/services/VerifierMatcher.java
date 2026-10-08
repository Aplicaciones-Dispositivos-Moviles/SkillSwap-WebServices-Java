package com.innovify.skillswap.assessmentpeerreview.domain.services;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import java.util.Optional;

/** Chooses the verifier who takes a case. */
public interface VerifierMatcher {

    /**
     * The available verifier enabled for the skill with the fewest open cases, never the student of the case;
     * ties go to the lowest user id. Empty when nobody qualifies.
     */
    Optional<VerifierProfile> findVerifier(String skillTag, int studentId, Iterable<VerifierCandidate> candidates);
}
