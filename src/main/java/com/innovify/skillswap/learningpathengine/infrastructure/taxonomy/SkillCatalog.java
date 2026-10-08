package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.shared.infrastructure.json.JsonException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The internal skill taxonomy, loaded from the skill-catalog.json resource. It is validated when loaded, so an
 * inconsistent catalog stops the application at startup instead of failing later.
 */
public final class SkillCatalog {

    public static final String RESOURCE_NAME = "/skill-catalog.json";

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final List<SkillDefinition> skills;
    private final Map<String, SkillDefinition> byTag = new HashMap<>();

    private SkillCatalog(List<SkillDefinition> skills) {
        this.skills = List.copyOf(skills);
        skills.forEach(skill -> byTag.put(skill.tag(), skill));
    }

    /** The skills in catalog order. */
    public List<SkillDefinition> skills() {
        return skills;
    }

    public Optional<SkillDefinition> find(String tag) {
        return Optional.ofNullable(byTag.get(tag));
    }

    /**
     * @throws IllegalStateException when the resource is missing or the catalog is not valid
     */
    public static SkillCatalog loadEmbedded() {
        try (InputStream stream = SkillCatalog.class.getResourceAsStream(RESOURCE_NAME)) {
            if (stream == null) {
                throw new IllegalStateException("The resource '" + RESOURCE_NAME + "' was not found.");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("The resource '" + RESOURCE_NAME + "' could not be read.", e);
        }
    }

    /**
     * @throws IllegalStateException when the text is not a valid catalog
     */
    public static SkillCatalog parse(String json) {
        String text = json != null && !json.isEmpty() && json.charAt(0) == BYTE_ORDER_MARK ? json.substring(1) : json;

        Object document;
        try {
            document = Json.parse(text);
        } catch (JsonException e) {
            throw new IllegalStateException("Invalid skill catalog: it is not valid JSON.", e);
        }

        if (!(document instanceof Map<?, ?> root) || !(root.get("skills") instanceof List<?> entries)
                || entries.isEmpty()) {
            throw invalid("it has no skills");
        }

        List<SkillDefinition> skills = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> fields)) {
                throw invalid("a skill is not an object");
            }
            skills.add(new SkillDefinition(
                    text(fields.get("tag")),
                    text(fields.get("name")),
                    text(fields.get("category")),
                    textList(fields.get("keywords")),
                    textList(fields.get("prerequisites"))));
        }

        validate(skills);
        return new SkillCatalog(skills);
    }

    private static void validate(List<SkillDefinition> skills) {
        Set<String> tags = new HashSet<>();
        for (SkillDefinition skill : skills) {
            if (skill.tag().isBlank()) {
                throw invalid("a skill has no tag");
            }
            if (!tags.add(skill.tag())) {
                throw invalid("the tag '" + skill.tag() + "' is repeated");
            }
            if (skill.name().isBlank()) {
                throw invalid("the skill '" + skill.tag() + "' has no name");
            }
            if (skill.keywords().stream().noneMatch(keyword -> !TextNormalizer.normalize(keyword).isEmpty())) {
                throw invalid("the skill '" + skill.tag() + "' has no usable keyword");
            }
        }

        Map<String, SkillDefinition> byTag = new HashMap<>();
        skills.forEach(skill -> byTag.put(skill.tag(), skill));
        for (SkillDefinition skill : skills) {
            for (String prerequisite : skill.prerequisites()) {
                if (prerequisite.equals(skill.tag())) {
                    throw invalid("the skill '" + skill.tag() + "' requires itself");
                }
                if (!tags.contains(prerequisite)) {
                    throw invalid("the skill '" + skill.tag() + "' requires the unknown skill '" + prerequisite + "'");
                }
            }
        }

        Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done
        for (SkillDefinition skill : skills) {
            visit(skill.tag(), "start", byTag, state);
        }
    }

    private static void visit(String tag, String trail, Map<String, SkillDefinition> byTag,
                              Map<String, Integer> state) {
        Integer current = state.get(tag);
        if (current != null && current == 2) {
            return;
        }
        if (current != null && current == 1) {
            throw invalid("there is a prerequisite cycle: " + trail + " -> " + tag);
        }

        state.put(tag, 1);
        for (String prerequisite : byTag.get(tag).prerequisites()) {
            visit(prerequisite, trail + " -> " + tag, byTag, state);
        }
        state.put(tag, 2);
    }

    private static String text(Object value) {
        return value instanceof String string ? string.strip() : "";
    }

    private static List<String> textList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String string) {
                result.add(string);
            }
        }
        return List.copyOf(result);
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid skill catalog: " + reason + ".");
    }
}
