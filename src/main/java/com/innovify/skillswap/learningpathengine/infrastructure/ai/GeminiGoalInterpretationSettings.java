package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the interpretation of the goal with Gemini, from {@code gemini.goal-interpretation.*}
 * (environment: GEMINI_GOAL_INTERPRETATION_ENABLED, GEMINI_GOAL_INTERPRETATION_TIMEOUT_SECONDS). The key, the
 * model chain, the retries and the per-attempt timeout are the common ones of {@link GeminiSettings}.
 *
 * @param enabled        false to interpret the goals only by keywords, without calling Gemini
 * @param timeoutSeconds maximum time for the whole interpretation (never more than gemini.total-timeout-seconds):
 *                       the student is waiting, so after it the keyword matcher answers instead
 */
@ConfigurationProperties(prefix = "gemini.goal-interpretation")
public record GeminiGoalInterpretationSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int timeoutSeconds) {

    public GeminiGoalInterpretationSettings {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "The setting gemini.goal-interpretation.timeout-seconds must be greater than zero.");
        }
    }
}
