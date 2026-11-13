package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;

/** Contract for calculating the employability of a student from the skills they certified. */
public interface EmployabilityScoreCalculator {

    /** @throws com.innovify.skillswap.shared.domain.exceptions.DomainException when the count is negative */
    EmployabilityScore calculate(int verifiedSkillsCount);
}
