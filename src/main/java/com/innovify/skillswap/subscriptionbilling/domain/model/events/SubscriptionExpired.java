package com.innovify.skillswap.subscriptionbilling.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A subscription expired ("Suscripción vencida"): the student is back on the free plan. Nothing of theirs is
 * deleted; the other contexts adapt to the limits of the free plan.
 *
 * @param subscriptionId the subscription
 * @param studentId      the student
 * @param expiredAt      when it was marked expired
 */
public record SubscriptionExpired(int subscriptionId, int studentId, Instant expiredAt) implements DomainEvent {
}
