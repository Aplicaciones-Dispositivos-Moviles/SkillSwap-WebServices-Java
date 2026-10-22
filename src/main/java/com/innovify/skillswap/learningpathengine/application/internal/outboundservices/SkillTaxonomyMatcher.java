package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

import java.util.List;

/**
 * Interprets free text against the skill taxonomy, decoupling the application from the matching technique
 * (keywords today, possibly semantic search later).
 */
public interface SkillTaxonomyMatcher {

    /** The tags of the skills the text refers to. Empty when it matches none. */
    List<String> match(String text);

    /**
     * The skills the text may refer to, the best fit first. Empty when it matches none. A matcher that cannot tell
     * how well each skill fits keeps the order of {@link #match(String)} and gives every skill the highest affinity.
     */
    default List<SkillCandidate> rank(String text) {
        return match(text).stream().map(tag -> new SkillCandidate(tag, 1.0)).toList();
    }
}
