package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import java.util.List;

/**
 * Interprets free text by keyword matching against the skill catalog. A keyword matches only as a whole word or
 * phrase ("java" does not match "javascript"), ignoring case and accents. No embeddings or external service are
 * involved.
 */
public class KeywordSkillTaxonomyMatcher implements SkillTaxonomyMatcher {

    private record Entry(String tag, List<String> paddedKeywords) {
    }

    private final List<Entry> entries;

    public KeywordSkillTaxonomyMatcher(SkillCatalog catalog) {
        this.entries = catalog.skills().stream()
                .map(skill -> new Entry(skill.tag(), skill.keywords().stream()
                        .map(TextNormalizer::normalize)
                        .filter(keyword -> !keyword.isEmpty())
                        .map(keyword -> " " + keyword + " ")
                        .distinct()
                        .toList()))
                .toList();
    }

    @Override
    public List<String> match(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        String padded = " " + normalized + " ";
        return entries.stream()
                .filter(entry -> entry.paddedKeywords().stream().anyMatch(padded::contains))
                .map(Entry::tag)
                .toList();
    }
}
