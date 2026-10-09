package com.innovify.skillswap.subscriptionbilling.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A student got the paid plan: the gateway confirmed their purchase.
 *
 * @param subscriptionId   the new subscription
 * @param studentId        the student
 * @param currentPeriodEnd when the period already paid ends
 */
public record SubscriptionActivated(int subscriptionId, int studentId, Instant currentPeriodEnd)
        implements DomainEvent {
}
