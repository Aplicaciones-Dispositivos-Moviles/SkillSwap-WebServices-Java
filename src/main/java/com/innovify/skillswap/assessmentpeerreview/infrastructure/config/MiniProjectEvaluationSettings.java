package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the preliminary evaluation of the mini-project deliveries from {@code mini-project-evaluation.*}
 * (environment: MINI_PROJECT_EVALUATION_ENABLED, MINI_PROJECT_EVALUATION_TIMEOUT_SECONDS). It uses the Gemini key,
 * models and retries of {@code gemini.*}.
 *
 * @param enabled        whether Gemini evaluates the deliveries; when false every delivery goes to a verifier
 * @param timeoutSeconds the time one evaluation may take (never more than gemini.total-timeout-seconds)
 * @param asynchronous   whether the evaluation runs in the background, so the delivery is answered at once; only the
 *                       tests turn it off, to evaluate in the same thread
 */
@ConfigurationProperties(prefix = "mini-project-evaluation")
public record MiniProjectEvaluationSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("60") int timeoutSeconds,
        @DefaultValue("true") boolean asynchronous) {

    public MiniProjectEvaluationSettings {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "The setting mini-project-evaluation.timeout-seconds must be greater than zero.");
        }
    }
}
