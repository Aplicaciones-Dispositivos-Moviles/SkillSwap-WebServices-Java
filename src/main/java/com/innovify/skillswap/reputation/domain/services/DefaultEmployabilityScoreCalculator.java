package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/** Employability = 10 points per certified skill, with a maximum of 100. */
public class DefaultEmployabilityScoreCalculator implements EmployabilityScoreCalculator {

    public static final int POINTS_PER_SKILL = 10;

    @Override
    public EmployabilityScore calculate(int verifiedSkillsCount) {
        if (verifiedSkillsCount < 0) {
            throw new DomainException("The number of verified skills cannot be negative.");
        }

        long score = Math.min(EmployabilityScore.MAX, (long) verifiedSkillsCount * POINTS_PER_SKILL);
        return new EmployabilityScore((int) score);
    }
}
