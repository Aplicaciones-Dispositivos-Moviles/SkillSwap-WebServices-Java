package com.innovify.skillswap.subscriptionbilling.domain.model.commands;

/**
 * Activates the subscription of a purchase the student just made in the app. The purchase is verified with the
 * payment gateway: nothing the client sends is trusted.
 *
 * @param studentId the authenticated student, used as the app user id of the gateway
 * @param productId the store product the app says it bought, or null to accept any that grants the paid plan
 */
public record CreateSubscriptionCommand(int studentId, String productId) {
}
