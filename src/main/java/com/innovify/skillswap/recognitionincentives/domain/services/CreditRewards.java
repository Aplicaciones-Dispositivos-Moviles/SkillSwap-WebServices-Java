package com.innovify.skillswap.recognitionincentives.domain.services;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * What a verifier earns. A resolved case pays according to the work reviewed: a mini-project takes longer to
 * review than a quiz, so it pays more. The decision does not matter: an approval and a rejection pay the same, so
 * there is no incentive to decide in one direction.
 */
public final class CreditRewards {

    public static final int PER_RESOLVED_MINI_PROJECT = 40;
    public static final int PER_RESOLVED_QUIZ = 25;

    private CreditRewards() {
    }

    /** @throws DomainException when the type of the case is missing */
    public static Credits forResolvedCase(ResolvedCaseType caseType) {
        if (caseType == null) {
            throw new DomainException("The type of the resolved case is required.");
        }
        return switch (caseType) {
            case MINI_PROJECT -> new Credits(PER_RESOLVED_MINI_PROJECT);
            case QUIZ -> new Credits(PER_RESOLVED_QUIZ);
        };
    }
}
