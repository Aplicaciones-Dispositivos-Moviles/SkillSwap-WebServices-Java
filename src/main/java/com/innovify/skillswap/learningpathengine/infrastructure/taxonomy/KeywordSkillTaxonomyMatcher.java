package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillCandidate;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Interprets free text by keyword matching against the skill catalog. A keyword matches only as a whole word or
 * phrase ("java" does not match "javascript"), ignoring case and accents. No embeddings or external service are
 * involved.
 *
 * <p>The affinity of a skill goes from 0.5 (some keyword of the skill is in the text) to 1 (the keywords of the skill
 * cover every word of the text): 0.5 plus half of the share of the words of the text that belong to the keywords
 * found. A skill with no keyword in the text is not a candidate.
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

    @Override
    public List<SkillCandidate> rank(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        String padded = " " + normalized + " ";
        Set<String> words = new LinkedHashSet<>(Arrays.asList(normalized.split(" ")));
        List<SkillCandidate> ranked = new ArrayList<>();
        for (Entry entry : entries) {
            Set<String> covered = new HashSet<>();
            for (String keyword : entry.paddedKeywords()) {
                if (padded.contains(keyword)) {
                    covered.addAll(Arrays.asList(keyword.strip().split(" ")));
                }
            }
            if (!covered.isEmpty()) {
                double affinity = 0.5 + 0.5 * covered.size() / words.size();
                ranked.add(new SkillCandidate(entry.tag(), Math.round(affinity * 100) / 100.0));
            }
        }

        // The sort is stable: skills with the same affinity keep the order of the catalog.
        ranked.sort(Comparator.comparingDouble(SkillCandidate::affinity).reversed());
        return List.copyOf(ranked);
    }
}
