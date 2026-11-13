package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;

/**
 * The goal declared by the student in free text, with its translation to the internal skill taxonomy.
 *
 * @param rawText         the free text, trimmed (at most {@value #MAX_RAW_TEXT_LENGTH} characters)
 * @param mappedSkillTags the taxonomy skills resolved from the text, without blanks or duplicates (at least one)
 */
public record CareerGoal(String rawText, List<String> mappedSkillTags) {

    public static final int MAX_RAW_TEXT_LENGTH = 500;

    public CareerGoal {
        if (rawText == null || rawText.isBlank()) {
            throw new DomainException("The goal text cannot be empty.");
        }
        String text = rawText.strip();
        if (text.length() > MAX_RAW_TEXT_LENGTH) {
            throw new DomainException(
                    "The goal text cannot exceed %d characters.".formatted(MAX_RAW_TEXT_LENGTH));
        }

        List<String> tags = (mappedSkillTags == null ? List.<String>of() : mappedSkillTags).stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        if (tags.isEmpty()) {
            throw new DomainException("The goal could not be interpreted: no skill of the taxonomy matches it.");
        }

        rawText = text;
        mappedSkillTags = tags;
    }
}
