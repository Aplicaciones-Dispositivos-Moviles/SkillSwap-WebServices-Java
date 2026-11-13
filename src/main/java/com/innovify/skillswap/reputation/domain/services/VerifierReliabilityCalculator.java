package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;

/** Contract for calculating the reliability of a verifier from an explainable set of rules. */
public interface VerifierReliabilityCalculator {

    /**
     * Starts from the full score and discounts every overturned decision, every sanction and every case whose
     * deadline the verifier missed, never going below zero. The number of resolved cases is part of the record but does not change the score.
     *
     * @throws com.innovify.skillswap.shared.domain.exceptions.DomainException when a counter is negative
     */
    ReliabilityScore calculate(int resolvedCasesCount, int overturnedDecisionsCount, int sanctionsCount,
                               int missedDeadlinesCount);

    /** Same as above for a verifier who never missed a deadline. */
    default ReliabilityScore calculate(int resolvedCasesCount, int overturnedDecisionsCount, int sanctionsCount) {
        return calculate(resolvedCasesCount, overturnedDecisionsCount, sanctionsCount, 0);
    }
}
