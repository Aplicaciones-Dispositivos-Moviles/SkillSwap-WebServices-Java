package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import java.util.List;

/** One skill of the catalog. */
public record SkillDefinition(String tag, String name, String category, List<String> keywords,
                              List<String> prerequisites) {
}
