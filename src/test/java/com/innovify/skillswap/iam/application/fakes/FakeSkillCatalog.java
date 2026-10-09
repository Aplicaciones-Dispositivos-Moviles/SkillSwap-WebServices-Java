package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A tiny skill catalog: a skill matches when the text contains one of its keywords. */
public class FakeSkillCatalog implements SkillCatalogContextFacade {

    private final Map<String, String> keywordToTag = new LinkedHashMap<>(Map.of(
            "java", "java-language",
            "react", "react",
            "sql", "sql-databases",
            "testing", "software-testing"));

    @Override
    public List<String> matchSkillTags(String text) {
        if (text == null) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        keywordToTag.forEach((keyword, tag) -> {
            if (normalized.matches(".*\\b" + keyword + "\\b.*") && !tags.contains(tag)) {
                tags.add(tag);
            }
        });
        return tags;
    }
}
