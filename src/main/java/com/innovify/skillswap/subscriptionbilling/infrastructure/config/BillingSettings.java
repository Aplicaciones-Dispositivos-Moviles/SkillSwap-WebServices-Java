package com.innovify.skillswap.subscriptionbilling.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Billing settings from {@code billing.*}.
 *
 * @param expirationCheckEnabled  whether the subscriptions whose period ended are checked periodically
 * @param expirationCheckInterval how often they are checked (ISO-8601 or 1h, 30m...)
 * @param simulatedPeriod         how long a period of the simulated gateway lasts, to try the expiration in demos
 */
@ConfigurationProperties(prefix = "billing")
public record BillingSettings(
        @DefaultValue("true") boolean expirationCheckEnabled,
        @DefaultValue("1h") Duration expirationCheckInterval,
        @DefaultValue("30d") Duration simulatedPeriod) {

    public BillingSettings {
        if (expirationCheckInterval == null || expirationCheckInterval.isNegative()
                || expirationCheckInterval.isZero()) {
            throw new IllegalArgumentException("The setting billing.expiration-check-interval must be positive.");
        }
        if (simulatedPeriod == null || simulatedPeriod.isNegative() || simulatedPeriod.isZero()) {
            throw new IllegalArgumentException("The setting billing.simulated-period must be positive.");
        }
    }
}
