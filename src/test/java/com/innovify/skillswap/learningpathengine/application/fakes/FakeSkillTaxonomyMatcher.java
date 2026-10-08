package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Matches a text to skills when it contains a phrase (case-insensitive). */
public class FakeSkillTaxonomyMatcher implements SkillTaxonomyMatcher {

    private final Map<String, String> tagByPhrase;

    public FakeSkillTaxonomyMatcher(Map<String, String> tagByPhrase) {
        this.tagByPhrase = tagByPhrase;
    }

    public static FakeSkillTaxonomyMatcher sample() {
        return new FakeSkillTaxonomyMatcher(Map.of(
                "jwt", "authentication-jwt",
                "rest", "rest-api-design",
                "http", "http-basics",
                "sql", "sql-fundamentals"));
    }

    @Override
    public List<String> match(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return tagByPhrase.entrySet().stream()
                .filter(rule -> lower.contains(rule.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .sorted()
                .toList();
    }
}
