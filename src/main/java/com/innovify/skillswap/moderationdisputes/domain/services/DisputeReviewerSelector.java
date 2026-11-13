package com.innovify.skillswap.moderationdisputes.domain.services;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Domain service: chooses who reviews a dispute. The final decision belongs to a Verificador senior, so the least
 * loaded available senior is chosen. While the platform has no senior available (it takes 100 resolved cases to
 * reach the Gold rank), the dispute does not wait indefinitely: the least loaded available verifier takes it, and the
 * dispute records that it went to a non-senior. The parties of the dispute (the owner of the certificate, whoever
 * raised it) are never chosen. Ties go to the lowest user id.
 */
public class DisputeReviewerSelector {

    private static final Comparator<ReviewerCandidate> LEAST_LOADED =
            Comparator.comparingInt(ReviewerCandidate::workload).thenComparingInt(ReviewerCandidate::userId);

    public Optional<ReviewerChoice> choose(Collection<Integer> excludedUserIds, List<ReviewerCandidate> candidates) {
        Set<Integer> excluded = excludedUserIds == null ? Set.of() : Set.copyOf(excludedUserIds);
        List<ReviewerCandidate> eligible = (candidates == null ? List.<ReviewerCandidate>of() : candidates).stream()
                .filter(candidate -> candidate.userId() > 0 && !excluded.contains(candidate.userId()))
                .toList();

        Optional<ReviewerCandidate> senior = eligible.stream().filter(ReviewerCandidate::senior).min(LEAST_LOADED);
        if (senior.isPresent()) {
            return Optional.of(new ReviewerChoice(senior.get().userId(), true));
        }
        return eligible.stream().min(LEAST_LOADED).map(candidate -> new ReviewerChoice(candidate.userId(), false));
    }
}
