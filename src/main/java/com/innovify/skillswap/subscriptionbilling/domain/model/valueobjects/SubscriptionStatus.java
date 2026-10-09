package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

/**
 * State of the billing cycle of a subscription. A cancelled subscription keeps the paid plan until the end of the
 * period already paid; only an expired one sends the student back to the free plan.
 */
public enum SubscriptionStatus {
    ACTIVE("Active"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown SubscriptionStatus: " + value);
    }
}
