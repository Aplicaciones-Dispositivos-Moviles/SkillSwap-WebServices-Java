package com.innovify.skillswap.recognitionincentives.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * An amount of SkillCredits, internal non-monetary credits. It is never negative.
 *
 * @param value the amount
 */
public record Credits(int value) {

    public Credits {
        if (value < 0) {
            throw new DomainException("The amount of credits cannot be negative.");
        }
    }

    public boolean isPositive() {
        return value > 0;
    }
}
