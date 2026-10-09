package com.innovify.skillswap.recognitionincentives.domain.services;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;

/**
 * What a verifier earns. Every resolved case pays the same, whether it was approved or rejected, so there is no
 * incentive to decide in one direction.
 */
public final class CreditRewards {

    public static final int PER_RESOLVED_CASE = 10;

    private CreditRewards() {
    }

    public static Credits forResolvedCase() {
        return new Credits(PER_RESOLVED_CASE);
    }
}
