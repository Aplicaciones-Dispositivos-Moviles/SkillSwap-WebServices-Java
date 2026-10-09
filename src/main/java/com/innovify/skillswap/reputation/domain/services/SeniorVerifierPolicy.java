package com.innovify.skillswap.reputation.domain.services;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.reputation.domain.model.valueobjects.VerifierRank;

/**
 * Who is a Verificador senior: a verifier with the Gold rank (100 resolved cases or more) whose reliability is 90
 * or more on the 0 to 100 scale. It is not a role of the account: it is earned, and lost, with the record of the
 * verifier, so it is always calculated from it.
 */
public final class SeniorVerifierPolicy {

    public static final int MIN_RELIABILITY = 90;

    private SeniorVerifierPolicy() {
    }

    public static boolean isSenior(int resolvedCasesCount, ReliabilityScore reliability) {
        return reliability != null
                && VerifierRank.fromResolvedCases(resolvedCasesCount) == VerifierRank.GOLD
                && reliability.value() >= MIN_RELIABILITY;
    }
}
