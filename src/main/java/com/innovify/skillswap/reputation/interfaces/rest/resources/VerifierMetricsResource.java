package com.innovify.skillswap.reputation.interfaces.rest.resources;

import java.util.List;

/** The verifiers with their reliability, ordered by it. */
public record VerifierMetricsResource(List<VerifierMetricResource> verifiers) {

    /** A verifier. averageResolutionHours and fullName are null when unknown. */
    public record VerifierMetricResource(int verifierUserId, String fullName, int score, String rank,
                                         int resolvedCasesCount, int overturnedDecisionsCount,
                                         int missedDeadlinesCount, boolean seniorVerifier, boolean suspended,
                                         Double averageResolutionHours) {
    }
}
