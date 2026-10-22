package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * A skill of the catalog that fits a goal.
 *
 * @param skillTag       the tag to send as {@code skillTag} when declaring the goal
 * @param skillName      the display name of the skill
 * @param description    short description of the skill; null while the catalog has none
 * @param nodeCount      how many nodes the path would have (the skill and the prerequisites not yet demonstrated)
 * @param estimatedWeeks estimated duration of that path in weeks
 * @param affinity       how well the skill fits the goal, from 0 to 1
 */
public record SkillOptionResource(
        String skillTag,
        String skillName,
        String description,
        Integer nodeCount,
        Integer estimatedWeeks,
        double affinity) {
}
