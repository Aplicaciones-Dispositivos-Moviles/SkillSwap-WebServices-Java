package com.innovify.skillswap.recognitionincentives.domain.model.commands;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;

/**
 * Redeems a benefit with the credits of a user.
 *
 * @param userId         the owner of the wallet
 * @param item           the benefit
 * @param idempotencyKey the key of this attempt, or null; retrying with the same key answers the movement already
 *                       recorded instead of charging again
 */
public record RedeemCommand(int userId, RedemptionItem item, IdempotencyKey idempotencyKey) {

    /** A redemption without idempotency key: every call charges. */
    public RedeemCommand(int userId, RedemptionItem item) {
        this(userId, item, null);
    }
}
