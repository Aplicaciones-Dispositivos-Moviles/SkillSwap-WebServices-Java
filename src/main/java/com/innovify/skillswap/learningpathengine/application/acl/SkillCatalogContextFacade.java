package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * Anti-corruption facade through which other bounded contexts (Identity &amp; Access, for the interest profile)
 * relate free text to the internal skill catalog, without depending on the matching technique.
 */
public interface SkillCatalogContextFacade {

    /** The tags of the catalog skills the text refers to, without duplicates. Empty when it matches none. */
    List<String> matchSkillTags(String text);
}
