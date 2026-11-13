package com.innovify.skillswap.subscriptionbilling;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import java.time.Duration;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Shared builders for the Subscription &amp; Billing tests. */
public final class TestData {

    public static final String PRODUCT = "skillswap_premium_monthly:monthly";

    private TestData() {
    }

    /** An active monthly subscription whose period ends in 30 days. */
    public static Subscription activeSubscription(int studentId) {
        return new Subscription(studentId, SubscriptionPlan.monthly(PRODUCT), "GPA.1234-5678-9012-34567",
                Instant.now().plus(Duration.ofDays(30)));
    }

    /** Moves the end of the paid period, even to the past, which the aggregate itself never allows. */
    public static Subscription withPeriodEnd(Subscription subscription, Instant periodEnd) {
        ReflectionTestUtils.setField(subscription, "currentPeriodEnd", periodEnd);
        return subscription;
    }
}
