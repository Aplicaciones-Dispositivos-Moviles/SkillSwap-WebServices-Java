package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates the statement and the rubric of a mini-project with the Gemini REST API (through {@link GeminiClient}).
 * The output of the model is never trusted: the rubric criteria go through the {@link RubricCriterion} constructor and
 * the whole draft through {@code MiniProject}, so anything that breaks the contract (a missing field, weights that do
 * not add up to 100...) is rejected.
 *
 * <p>Availability problems are retried and then handed over to the next model of the configured chain by the client.
 * Permanent errors and contract violations fail at once, as {@link IllegalStateException} or
 * {@link com.innovify.skillswap.shared.domain.exceptions.DomainException}.
 */
public class GeminiMiniProjectGenerator implements MiniProjectGenerationService {

    private static final Pattern SKILL_TAG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final GeminiClient client;

    public GeminiMiniProjectGenerator(GeminiClient client) {
        this.client = client;
    }

    @Override
    public MiniProjectDraft generateMiniProject(String skillTag) {
        // The tag goes into the prompt, so only the catalog's lowercase kebab-case format is accepted.
        if (skillTag == null || !SKILL_TAG.matcher(skillTag).matches()) {
            throw new IllegalArgumentException("The skill tag is not valid.");
        }
        return parse(client.generateJson(buildPrompt(skillTag)));
    }

    private static String buildPrompt(String skillTag) {
        String skill = skillTag.replace('-', ' ');
        return """
                You are an assessment writer for a platform that validates software engineering skills.
                Write ONE hands-on mini-project that a student can build alone in about 5 days to demonstrate this
                skill: "%1$s".

                Rules:
                - The statement describes what to build, the requirements and what to deliver: a public Git repository
                  with the code and a README. Plain text, no Markdown headings, under 3000 characters.
                - The title is short (under 100 characters).
                - The rubric has between 4 and 6 criteria. Each criterion has a short name (under 60 characters), a
                  one-sentence description (under 300 characters) and an integer weight from 1 to 100. The weights
                  must add up to exactly 100.
                - The criteria must be observable in the repository (code, tests, documentation, commit history), not
                  subjective impressions.
                - Write in English.

                Respond ONLY with a JSON object of this shape:
                {"title": string, "statement": string,
                 "rubric": [{"name": string, "description": string, "weight": number}]}
                """.formatted(skill);
    }

    private static MiniProjectDraft parse(String answerText) {
        Object parsed = GeminiClient.parseAnswer(answerText, "Gemini did not return the mini-project as a JSON object.");
        if (!(parsed instanceof Map<?, ?> fields)) {
            throw new IllegalStateException("Gemini did not return the mini-project as a JSON object.");
        }
        if (!(fields.get("rubric") instanceof List<?> items)) {
            throw new IllegalStateException("Gemini returned a mini-project without a rubric.");
        }

        List<RubricCriterion> rubric = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> criterion)) {
                throw new IllegalStateException("Gemini returned a rubric criterion that is not an object.");
            }
            // Without this check a missing weight would silently become 0.
            if (!(criterion.get("weight") instanceof Number weight) || weight.doubleValue() != weight.intValue()) {
                throw new IllegalStateException("A rubric criterion has no integer weight.");
            }
            rubric.add(new RubricCriterion(
                    criterion.get("name") instanceof String name ? name : "",
                    criterion.get("description") instanceof String description ? description : "",
                    weight.intValue()));
        }

        return new MiniProjectDraft(
                fields.get("title") instanceof String title ? title : "",
                fields.get("statement") instanceof String statement ? statement : "",
                List.copyOf(rubric));
    }
}
