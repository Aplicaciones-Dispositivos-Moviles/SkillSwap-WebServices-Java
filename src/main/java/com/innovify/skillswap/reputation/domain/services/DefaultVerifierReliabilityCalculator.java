package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Reliability = 100 - 15 per overturned decision - 25 per sanction - 5 per missed deadline, with a minimum of 0. A
 * missed deadline weighs less than an overturned decision: the case is reassigned and the student still gets a review.
 */
public class DefaultVerifierReliabilityCalculator implements VerifierReliabilityCalculator {

    public static final int OVERTURN_PENALTY = 15;
    public static final int SANCTION_PENALTY = 25;
    public static final int MISSED_DEADLINE_PENALTY = 5;

    @Override
    public ReliabilityScore calculate(int resolvedCasesCount, int overturnedDecisionsCount, int sanctionsCount,
                                      int missedDeadlinesCount) {
        if (resolvedCasesCount < 0 || overturnedDecisionsCount < 0 || sanctionsCount < 0
                || missedDeadlinesCount < 0) {
            throw new DomainException("The counters cannot be negative.");
        }

        long score = ReliabilityScore.MAX
                - (long) overturnedDecisionsCount * OVERTURN_PENALTY
                - (long) sanctionsCount * SANCTION_PENALTY
                - (long) missedDeadlinesCount * MISSED_DEADLINE_PENALTY;
        return new ReliabilityScore((int) Math.max(ReliabilityScore.MIN, score));
    }
}
