package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the assessment questions with the Gemini REST API. The model's output is never trusted: every
 * question goes through the {@link Question} constructor, so anything that breaks the contract (wrong count,
 * repeated answers, missing correct index...) is rejected.
 *
 * <p>Availability problems (429, 5xx, no answer in time, a connection that fails, a retired model) are retried
 * and then handed over to the next model of the configured chain. Permanent errors and contract violations are
 * not: they fail at once, as {@link IllegalStateException} or {@link com.innovify.skillswap.shared.domain.exceptions.DomainException}.
 */
public class GeminiQuestionGenerator implements QuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GeminiQuestionGenerator.class);

    private static final Pattern SKILL_TAG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```(?:json)?\\s*|\\s*```\\s*$");
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final int MAX_ERROR_TEXT = 500;

    private final GeminiSettings settings;
    private final HttpClient httpClient;

    public GeminiQuestionGenerator(GeminiSettings settings, HttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    @Override
    public List<Question> generateQuestions(String skillTag) {
        // The tag goes into the prompt, so only the catalog's lowercase kebab-case format is accepted.
        if (skillTag == null || !SKILL_TAG.matcher(skillTag).matches()) {
            throw new IllegalArgumentException("The skill tag is not valid.");
        }

        // One overall budget for the whole operation, however many retries and models are tried.
        long deadline = System.nanoTime() + Duration.ofSeconds(settings.totalTimeoutSeconds()).toNanos();
        String answerText = requestFromAnyModel(skillTag, deadline);
        return parseQuestions(answerText);
    }

    private String requestFromAnyModel(String skillTag, long deadline) {
        List<String> models = settings.modelChain();
        for (int index = 0; index < models.size(); index++) {
            try {
                return requestFromModel(models.get(index), skillTag, deadline);
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
    private String requestFromModel(String model, String skillTag, long deadline) {
        boolean withThinking = settings.thinkingLevel() != null;
        try {
            return requestWithRetries(model, buildRequestBody(skillTag, withThinking), deadline);
        } catch (ThinkingNotSupportedException exception) {
            if (!withThinking) {
                throw exception;
            }
            log.warn("Gemini model {} does not accept thinkingLevel; asking again without it", model);
            return requestWithRetries(model, buildRequestBody(skillTag, false), deadline);
        }
    }

    private String buildRequestBody(String skillTag, boolean withThinking) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        if (withThinking) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", settings.thinkingLevel()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(skillTag))))));
        body.put("generationConfig", generationConfig);
        return Json.write(body);
    }

    private static String buildPrompt(String skillTag) {
        String skill = skillTag.replace('-', ' ');
        return """
                You are an assessment writer for a platform that validates software engineering skills.
                Write exactly %1$d multiple-choice questions that test practical understanding of this skill: "%2$s".

                Rules:
                - Each question has exactly %3$d answer options and exactly one correct option.
                - The options must be plausible and clearly different from each other.
                - Do not use "all of the above" or "none of the above".
                - Vary the position of the correct option across questions.
                - The questions must be different from each other and cover different sub-topics of the skill.
                - Write in English. Keep each question under 300 characters and each option under 150 characters.

                Respond ONLY with a JSON array of exactly %1$d objects, each with this shape:
                {"question": string, "answers": [string, string, string, string], "correctIndex": number from 0 to 3}
                """.formatted(AssessmentBlueprint.QUESTION_COUNT, skill, Question.ANSWER_COUNT);
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

    private static List<Question> parseQuestions(String answerText) {
        String cleaned = CODE_FENCE.matcher(answerText).replaceAll("").strip();
        Object parsed = parseJson(cleaned, "Gemini did not return the questions as a JSON array.");

        if (!(parsed instanceof List<?> items) || items.size() != AssessmentBlueprint.QUESTION_COUNT) {
            throw new IllegalStateException("Gemini returned %d questions instead of %d.".formatted(
                    parsed instanceof List<?> list ? list.size() : 0, AssessmentBlueprint.QUESTION_COUNT));
        }

        List<Question> questions = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> fields)) {
                throw new IllegalStateException("Gemini returned a question that is not an object.");
            }
            // Without this check a missing index would silently become 0.
            if (!(fields.get("correctIndex") instanceof Number index) || index.doubleValue() != index.intValue()) {
                throw new IllegalStateException("A question has no correct answer index.");
            }
            questions.add(new Question(
                    fields.get("question") instanceof String text ? text : "",
                    answersOf(fields.get("answers")),
                    index.intValue()));
        }
        return List.copyOf(questions);
    }

    private static List<String> answersOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> answers)) {
            throw new IllegalStateException("A question has answers that are not a list.");
        }
        List<String> result = new ArrayList<>();
        for (Object answer : answers) {
            if (!(answer instanceof String text)) {
                throw new IllegalStateException("A question has an answer that is not text.");
            }
            result.add(text);
        }
        return result;
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
