package com.innovify.skillswap.recognitionincentives.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A student redeemed an advanced path unlock: Learning Path Engine grants them the advanced path.
 *
 * @param userId       the student who redeemed it
 * @param redemptionId the redeemed credit transaction, which grants a single unlock
 */
public record AdvancedPathUnlockRedeemed(int userId, int redemptionId) implements DomainEvent {
}
