package com.innovify.skillswap.subscriptionbilling.domain.model.commands;

/**
 * Checks a subscription whose paid period ended without news from the gateway: it is renewed when the gateway
 * reports a new period, and expired otherwise.
 *
 * @param subscriptionId the subscription
 */
public record ExpireSubscriptionCommand(int subscriptionId) {
}
