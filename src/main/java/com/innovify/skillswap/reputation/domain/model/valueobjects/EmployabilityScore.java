package com.innovify.skillswap.reputation.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Employability demonstrated by a student, from 0 to 100.
 *
 * @param value the score
 */
public record EmployabilityScore(int value) {

    public static final int MIN = 0;
    public static final int MAX = 100;

    public EmployabilityScore {
        if (value < MIN || value > MAX) {
            throw new DomainException("The employability score must be between %d and %d.".formatted(MIN, MAX));
        }
    }
}
