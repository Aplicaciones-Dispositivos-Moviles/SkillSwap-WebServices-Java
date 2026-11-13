package com.innovify.skillswap.subscriptionbilling.domain.model.commands;

/**
 * Stops the renewals of a subscription; the plan is kept until the end of the period already paid.
 *
 * @param subscriptionId the subscription
 * @param studentId      the authenticated student, who must own it
 */
public record CancelSubscriptionCommand(int subscriptionId, int studentId) {
}
