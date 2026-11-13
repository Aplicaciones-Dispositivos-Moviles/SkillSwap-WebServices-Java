package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gemini settings from {@code gemini.*} (environment: GEMINI_API_KEY, GEMINI_MODEL, GEMINI_FALLBACK_MODELS as a
 * comma-separated list, GEMINI_THINKING_LEVEL...). The API key is required: the application does not start
 * without it. Never commit a real key.
 *
 * @param apiKey                 API key, sent in the x-goog-api-key header
 * @param model                  main model
 * @param fallbackModels         models tried, in order, when the main one cannot serve the request (overloaded,
 *                               rate limited, not answering in time or retired). Empty means no fallback
 * @param thinkingLevel          optional reasoning effort ("low", ...). When blank the parameter is not sent, and a
 *                               model that rejects it is asked again without it
 * @param timeoutSeconds         maximum time to wait for each attempt
 * @param totalTimeoutSeconds    maximum time for the whole operation, however many retries and models are tried
 * @param maxRetries             retries per model after a transient failure (429, 500, 502, 503, 504)
 * @param retryDelayMilliseconds wait before the first retry; it doubles on each following one
 * @param baseUrl                base URL of the API; only tests change it
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiSettings(
        String apiKey,
        @DefaultValue("gemini-3.5-flash") String model,
        List<String> fallbackModels,
        String thinkingLevel,
        @DefaultValue("30") int timeoutSeconds,
        @DefaultValue("60") int totalTimeoutSeconds,
        @DefaultValue("1") int maxRetries,
        @DefaultValue("1000") int retryDelayMilliseconds,
        @DefaultValue("https://generativelanguage.googleapis.com/v1beta/") String baseUrl) {

    public GeminiSettings {
        requireValue(apiKey, "gemini.api-key (GEMINI_API_KEY)");
        requireValue(model, "gemini.model (GEMINI_MODEL)");
        requireValue(baseUrl, "gemini.base-url");
        requirePositive(timeoutSeconds, "gemini.timeout-seconds");
        requirePositive(totalTimeoutSeconds, "gemini.total-timeout-seconds");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("The setting gemini.max-retries cannot be negative.");
        }
        if (retryDelayMilliseconds < 0) {
            throw new IllegalArgumentException("The setting gemini.retry-delay-milliseconds cannot be negative.");
        }
        fallbackModels = fallbackModels == null ? List.of() : List.copyOf(fallbackModels);
        thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() ? null : thinkingLevel.strip();
        baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /** The main model followed by the fallbacks, without blanks or repeats (case-insensitive). */
    public List<String> modelChain() {
        List<String> chain = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        candidates.add(model);
        candidates.addAll(fallbackModels);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String trimmed = candidate.strip();
            if (chain.stream().noneMatch(trimmed::equalsIgnoreCase)) {
                chain.add(trimmed);
            }
        }
        return List.copyOf(chain);
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The setting " + name + " is required.");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("The setting " + name + " must be greater than zero.");
        }
    }
}
