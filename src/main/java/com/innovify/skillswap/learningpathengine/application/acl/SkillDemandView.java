package com.innovify.skillswap.learningpathengine.application.acl;

/**
 * How many learning paths asked for a skill in a period.
 *
 * @param skillTag  the skill of the catalog
 * @param pathCount the paths created in the period whose goal was interpreted as that skill
 */
public record SkillDemandView(String skillTag, int pathCount) {
}
