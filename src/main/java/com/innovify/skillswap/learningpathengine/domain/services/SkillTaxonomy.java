package com.innovify.skillswap.learningpathengine.domain.services;

import java.util.List;

/** The internal catalog of skills and their prerequisites. */
public interface SkillTaxonomy {

    boolean contains(String skillTag);

    /**
     * Direct prerequisites of a skill.
     *
     * @throws com.innovify.skillswap.shared.domain.exceptions.DomainException when the skill is not in the
     *                                                                         taxonomy
     */
    List<String> prerequisitesOf(String skillTag);

    /** The display name of a skill; the tag itself when the skill is unknown. */
    String nameOf(String skillTag);
}
