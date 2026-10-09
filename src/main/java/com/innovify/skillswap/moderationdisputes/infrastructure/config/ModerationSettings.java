package com.innovify.skillswap.moderationdisputes.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Moderation settings from {@code moderation.*}.
 *
 * @param assignmentRetryEnabled  whether the disputes that found no reviewer are retried periodically
 * @param assignmentRetryInterval how often they are retried (ISO-8601 or 15m, 1h...)
 */
@ConfigurationProperties(prefix = "moderation")
public record ModerationSettings(
        @DefaultValue("true") boolean assignmentRetryEnabled,
        @DefaultValue("15m") Duration assignmentRetryInterval) {

    public ModerationSettings {
        if (assignmentRetryInterval == null || assignmentRetryInterval.isNegative()
                || assignmentRetryInterval.isZero()) {
            throw new IllegalArgumentException("The setting moderation.assignment-retry-interval must be positive.");
        }
    }
}
