package com.innovify.skillswap.assessmentpeerreview.domain.services;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import java.util.Optional;
import java.util.Set;

/** Chooses the verifier who takes a case. */
public interface VerifierMatcher {

    /**
     * The available verifier enabled for the skill with the fewest open cases whose user is not excluded
     * (the parties of the case); ties go to the lowest user id. Empty when nobody qualifies.
     */
    Optional<VerifierProfile> findVerifier(String skillTag, Set<Integer> excludedUserIds,
                                           Iterable<VerifierCandidate> candidates);

    /** Same as above for a case that only excludes its student. */
    default Optional<VerifierProfile> findVerifier(String skillTag, int studentId,
                                                   Iterable<VerifierCandidate> candidates) {
        return findVerifier(skillTag, Set.of(studentId), candidates);
    }
}
