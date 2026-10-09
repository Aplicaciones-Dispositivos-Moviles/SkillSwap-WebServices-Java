package com.innovify.skillswap.reputation.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * The rank of a verifier, reached by the number of cases they resolved (never by their SkillCredits, so redeeming
 * credits does not lower it): Bronze from 0 to 29 resolved cases, Silver from 30 to 99 and Gold from 100.
 */
public enum VerifierRank {
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold");

    public static final int SILVER_MIN_RESOLVED_CASES = 30;
    public static final int GOLD_MIN_RESOLVED_CASES = 100;

    private final String value;

    VerifierRank(String value) {
        this.value = value;
    }

    /** The representation exposed by the API. */
    public String value() {
        return value;
    }

    /** @throws DomainException when the count is negative */
    public static VerifierRank fromResolvedCases(int resolvedCasesCount) {
        if (resolvedCasesCount < 0) {
            throw new DomainException("The number of resolved cases cannot be negative.");
        }
        if (resolvedCasesCount >= GOLD_MIN_RESOLVED_CASES) {
            return GOLD;
        }
        return resolvedCasesCount >= SILVER_MIN_RESOLVED_CASES ? SILVER : BRONZE;
    }
}
