package com.innovify.skillswap.recognitionincentives.domain.model.commands;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;

/**
 * Redeems a benefit with the credits of a user.
 *
 * @param userId the owner of the wallet
 * @param item   the benefit
 */
public record RedeemCommand(int userId, RedemptionItem item) {
}
