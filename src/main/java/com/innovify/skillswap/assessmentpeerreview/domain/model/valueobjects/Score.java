package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Result of grading an attempt: the correct answers over the total number of questions.
 *
 * @param value the correct answers
 * @param total the number of questions
 */
public record Score(int value, int total) {

    public Score {
        if (total <= 0) {
            throw new DomainException("The total score must be positive.");
        }
        if (value < 0 || value > total) {
            throw new DomainException("The score must be between 0 and the total.");
        }
    }
}
