package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.shared.infrastructure.json.JsonException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a prompt to the Gemini REST API and returns the text of the answer, asking for JSON. It is the single
 * entry point to Gemini of the Learning Path Engine, shared by the question generator and the goal interpreter,
 * so both use the same key, model chain, retries and timeouts ({@link GeminiSettings}).
 *
 * <p>Availability problems (429, 5xx, no answer in time, a connection that fails, a retired model) are retried
 * and then handed over to the next model of the configured chain, as {@link GeminiUnavailableException}.
 * Permanent errors (invalid key, bad request, blocked or empty answer) fail at once, as
 * {@link IllegalStateException}.
 */
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```(?:json)?\\s*|\\s*```\\s*$");
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final int MAX_ERROR_TEXT = 500;

    private final GeminiSettings settings;
    private final HttpClient httpClient;

    public GeminiClient(GeminiSettings settings, HttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    /** Asks for a JSON answer within the overall budget of the settings (gemini.total-timeout-seconds). */
    public String generateJson(String prompt) {
        return generateJson(prompt, Duration.ofSeconds(settings.totalTimeoutSeconds()));
    }

    /**
     * Asks for a JSON answer within the given budget (never more than gemini.total-timeout-seconds), however many
     * retries and models are tried.
     *
     * @return the text of the answer, without the reasoning parts
     */
    public String generateJson(String prompt, Duration budget) {
        Duration total = Duration.ofSeconds(settings.totalTimeoutSeconds());
        if (budget != null && budget.compareTo(total) < 0 && !budget.isNegative() && !budget.isZero()) {
            total = budget;
        }
        long deadline = System.nanoTime() + total.toNanos();
        return requestFromAnyModel(prompt, deadline);
    }

    /**
     * Parses the JSON of an answer, ignoring a Markdown code fence around it.
     *
     * @throws IllegalStateException when the answer is not valid JSON
     */
    public static Object parseAnswer(String answerText, String errorMessage) {
        String cleaned = CODE_FENCE.matcher(answerText == null ? "" : answerText).replaceAll("").strip();
        return parseJson(cleaned, errorMessage);
    }

    private String requestFromAnyModel(String prompt, long deadline) {
        List<String> models = settings.modelChain();
        for (int index = 0; index < models.size(); index++) {
            try {
                return requestFromModel(models.get(index), prompt, deadline);
            } catch (GeminiUnavailableException exception) {
                if (index == models.size() - 1) {
                    throw exception;
                }
                log.warn("Gemini model {} cannot serve the request; trying {}", models.get(index),
                        models.get(index + 1), exception);
            }
        }
        throw new IllegalStateException("No Gemini model is configured.");
    }

    /**
     * Asks one model. The reasoning-effort parameter is best effort: a model that rejects it is asked again
     * without it.
     */
    private String requestFromModel(String model, String prompt, long deadline) {
        boolean withThinking = settings.thinkingLevel() != null;
        try {
            return requestWithRetries(model, buildRequestBody(prompt, withThinking), deadline);
        } catch (ThinkingNotSupportedException exception) {
            if (!withThinking) {
                throw exception;
            }
            log.warn("Gemini model {} does not accept thinkingLevel; asking again without it", model);
            return requestWithRetries(model, buildRequestBody(prompt, false), deadline);
        }
    }

    private String buildRequestBody(String prompt, boolean withThinking) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        if (withThinking) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", settings.thinkingLevel()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", generationConfig);
        return Json.write(body);
    }

    /**
     * Sends the request to one model, retrying (with a doubling wait) only while the provider reports a
     * transient failure.
     */
    private String requestWithRetries(String model, String body, long deadline) {
        for (int attempt = 0; ; attempt++) {
            try {
                return send(model, body, attempt + 1, deadline);
            } catch (GeminiUnavailableException exception) {
                if (!exception.isRetryable() || attempt >= settings.maxRetries()) {
                    throw exception;
                }
                pause(settings.retryDelayMilliseconds() * (1L << attempt));
            }
        }
    }

    private String send(String model, String body, int attempt, long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new GeminiTimeoutException("Gemini did not answer within the allowed time.");
        }
        Duration timeout = Duration.ofSeconds(settings.timeoutSeconds());
        if (Duration.ofNanos(remainingNanos).compareTo(timeout) < 0) {
            timeout = Duration.ofNanos(remainingNanos);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.baseUrl() + "models/"
                        + URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20") + ":generateContent"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            log.warn("Gemini model {}, attempt {}: no answer after {} ms", model, attempt, elapsedMillis(started));
            throw new GeminiTimeoutException("Gemini did not answer in time.");
        } catch (IOException exception) {
            log.warn("Gemini model {}, attempt {}: the request failed: {}", model, attempt, exception.toString());
            throw new GeminiUnavailableException("Gemini could not be reached: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The request to Gemini was interrupted.", exception);
        }

        int status = response.statusCode();
        String content = response.body() == null ? "" : response.body();
        log.info("Gemini model {}, attempt {}: {} in {} ms", model, attempt, status, elapsedMillis(started));

        if (status >= 200 && status < 300) {
            return extractText(content);
        }

        String message = "Gemini model '%s' responded %d: %s".formatted(model, status, truncate(content));

        if (status == 400 && body.contains("thinkingConfig")
                && content.toLowerCase(Locale.ROOT).contains("thinking")) {
            throw new ThinkingNotSupportedException(message);
        }

        // A retired model will not come back: skip to the next one instead of retrying it.
        if (status == 404) {
            throw new GeminiUnavailableException(message, false);
        }

        throw TRANSIENT_STATUSES.contains(status)
                ? new GeminiUnavailableException(message)
                : new IllegalStateException(message);
    }

    private static void pause(long milliseconds) {
        if (milliseconds <= 0) {
            return;
        }
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The request to Gemini was interrupted.", exception);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static String extractText(String responseJson) {
        Object root = parseJson(responseJson, "Gemini did not return a JSON answer.");

        Object candidates = root instanceof Map<?, ?> map ? map.get("candidates") : null;
        if (!(candidates instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates (the request may have been blocked).");
        }

        if (!(list.get(0) instanceof Map<?, ?> candidate)) {
            throw new IllegalStateException("Gemini returned an answer without content.");
        }
        if (candidate.get("finishReason") instanceof String reason && !reason.equals("STOP")) {
            throw new IllegalStateException("Gemini stopped before completing the answer (" + reason + ").");
        }

        if (!(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new IllegalStateException("Gemini returned an answer without content.");
        }

        StringBuilder text = new StringBuilder();
        for (Object entry : parts) {
            if (!(entry instanceof Map<?, ?> part)) {
                continue;
            }
            if (Boolean.TRUE.equals(part.get("thought"))) {
                continue;
            }
            if (part.get("text") instanceof String partText) {
                text.append(partText);
            }
        }

        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty answer.");
        }
        return text.toString();
    }

    private static Object parseJson(String text, String errorMessage) {
        try {
            return Json.parse(text);
        } catch (JsonException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_TEXT ? text : text.substring(0, MAX_ERROR_TEXT) + "...";
    }

    private static final class ThinkingNotSupportedException extends IllegalStateException {

        ThinkingNotSupportedException(String message) {
            super(message);
        }
    }
}
