package com.innovify.skillswap.subscriptionbilling.infrastructure.payments.simulated;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link PaymentGateway} used when no RevenueCat API key is configured (local development, tests, demos). It keeps
 * the same contract without any store: every verification of a student without a simulated subscription is an
 * approved purchase of one period, which renews until it is cancelled. The state lives in memory, so it is lost
 * when the application restarts. It never charges anything.
 */
public class SimulatedPaymentGatewayAdapter implements PaymentGateway {

    public static final String TRANSACTION_PREFIX = "SIMULATED-";

    private record SimulatedSubscription(String productId, String transactionId, Instant expiresAt,
                                         boolean willRenew) {
    }

    private final Duration period;
    private final ConcurrentMap<Integer, SimulatedSubscription> subscriptions = new ConcurrentHashMap<>();

    /** @param period how long each simulated period lasts (a month in production-like demos) */
    public SimulatedPaymentGatewayAdapter(Duration period) {
        if (period == null || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("The simulated period must be positive.");
        }
        this.period = period;
    }

    @Override
    public PurchaseVerification verifyPurchase(int studentId, String productId) {
        Instant now = Instant.now();
        SimulatedSubscription state = subscriptions.compute(studentId, (id, current) -> {
            if (current == null) {
                String product = productId == null ? SubscriptionPlan.DEFAULT_MONTHLY_PRODUCT_ID : productId;
                return new SimulatedSubscription(product, TRANSACTION_PREFIX + id + "-" + now.toEpochMilli(),
                        now.plus(period), true);
            }
            if (current.expiresAt().isAfter(now)) {
                return current;
            }
            if (!current.willRenew()) {
                // Ended after a cancellation: it is forgotten, so a later purchase starts a new one.
                return null;
            }
            Instant renewed = current.expiresAt();
            while (!renewed.isAfter(now)) {
                renewed = renewed.plus(period);
            }
            return new SimulatedSubscription(current.productId(), current.transactionId(), renewed, true);
        });

        if (state == null) {
            return PurchaseVerification.inactive();
        }
        return new PurchaseVerification(true, state.productId(), state.expiresAt(), state.willRenew(),
                state.transactionId(), true);
    }

    @Override
    public void cancelRenewal(int studentId, String storeTransactionId) {
        subscriptions.computeIfPresent(studentId, (id, current) -> new SimulatedSubscription(current.productId(),
                current.transactionId(), current.expiresAt(), false));
    }
}
