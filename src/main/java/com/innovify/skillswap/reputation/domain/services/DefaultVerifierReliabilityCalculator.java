package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/** Reliability = 100 - 15 per overturned decision - 25 per sanction, with a minimum of 0. */
public class DefaultVerifierReliabilityCalculator implements VerifierReliabilityCalculator {

    public static final int OVERTURN_PENALTY = 15;
    public static final int SANCTION_PENALTY = 25;

    @Override
    public ReliabilityScore calculate(int resolvedCasesCount, int overturnedDecisionsCount, int sanctionsCount) {
        if (resolvedCasesCount < 0 || overturnedDecisionsCount < 0 || sanctionsCount < 0) {
            throw new DomainException("The counters cannot be negative.");
        }

        long score = ReliabilityScore.MAX
                - (long) overturnedDecisionsCount * OVERTURN_PENALTY
                - (long) sanctionsCount * SANCTION_PENALTY;
        return new ReliabilityScore((int) Math.max(ReliabilityScore.MIN, score));
    }
}
