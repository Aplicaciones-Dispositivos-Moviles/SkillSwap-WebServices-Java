package com.innovify.skillswap.assessmentpeerreview.domain.services;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;

/** Assigns cases to the least loaded qualified verifier. */
public class DefaultVerifierMatcher implements VerifierMatcher {

    @Override
    public Optional<VerifierProfile> findVerifier(String skillTag, int studentId,
                                                  Iterable<VerifierCandidate> candidates) {
        return StreamSupport.stream(candidates.spliterator(), false)
                .filter(candidate -> candidate.profile().isAvailable()
                        && candidate.profile().canReview(skillTag)
                        && candidate.profile().getVerifierUserId() != studentId)
                .min(Comparator.comparingInt(VerifierCandidate::openCaseCount)
                        .thenComparingInt(candidate -> candidate.profile().getVerifierUserId()))
                .map(VerifierCandidate::profile);
    }
}
