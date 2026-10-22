package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption facade through which other bounded contexts (Identity &amp; Access, for the interest profile)
 * relate free text to the internal skill catalog, without depending on the matching technique.
 */
public interface SkillCatalogContextFacade {

    /** The tags of the catalog skills the text refers to, without duplicates. Empty when it matches none. */
    List<String> matchSkillTags(String text);

    /** The display name of each skill; the tag itself when a skill is not in the catalog. Keys follow the given tags. */
    Map<String, String> getSkillNames(Collection<String> skillTags);
}
