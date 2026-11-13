package com.innovify.skillswap.reputation.application.queryservices;

import java.util.List;

/**
 * The verifiers with their reliability.
 *
 * @param verifiers the verifiers that have a reliability recorded, ordered by it
 */
public record VerifierMetrics(List<VerifierMetric> verifiers) {

    /**
     * A verifier.
     *
     * @param averageResolutionHours hours between the assignment and the resolution of their cases on average; null
     *                               when they resolved none
     * @param fullName               the name they registered; null when unknown
     */
    public record VerifierMetric(int verifierUserId, String fullName, int score, String rank, int resolvedCasesCount,
                                 int overturnedDecisionsCount, int missedDeadlinesCount, boolean seniorVerifier,
                                 boolean suspended, Double averageResolutionHours) {
    }
}
