package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

import java.util.List;

/**
 * Interprets free text against the skill taxonomy, decoupling the application from the matching technique
 * (keywords today, possibly semantic search later).
 */
public interface SkillTaxonomyMatcher {

    /** The tags of the skills the text refers to. Empty when it matches none. */
    List<String> match(String text);
}
