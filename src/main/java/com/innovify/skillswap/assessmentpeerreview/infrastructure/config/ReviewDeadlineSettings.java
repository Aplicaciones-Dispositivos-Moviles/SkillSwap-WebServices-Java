package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the review deadlines from {@code review-deadlines.*}.
 *
 * @param reassignmentEnabled whether the overdue cases are looked for and reassigned periodically
 * @param checkInterval       how often they are looked for (ISO-8601 or 15m, 1h...)
 */
@ConfigurationProperties(prefix = "review-deadlines")
public record ReviewDeadlineSettings(
        @DefaultValue("true") boolean reassignmentEnabled,
        @DefaultValue("15m") Duration checkInterval) {

    public ReviewDeadlineSettings {
        if (checkInterval == null || checkInterval.isNegative() || checkInterval.isZero()) {
            throw new IllegalArgumentException("The setting review-deadlines.check-interval must be positive.");
        }
    }
}
