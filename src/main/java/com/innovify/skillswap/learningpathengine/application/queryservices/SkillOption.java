package com.innovify.skillswap.learningpathengine.application.queryservices;

/**
 * A skill the student can choose for their goal.
 *
 * @param skillTag       the tag of the skill, as in the skill catalog
 * @param skillName      the display name of the skill
 * @param description    a short description; null while the catalog has none
 * @param nodeCount      how many nodes the path of this skill would have, given what the student already demonstrated
 * @param estimatedWeeks a simple estimate of the time to finish that path
 * @param affinity       how well the skill fits the goal, from 0 to 1
 */
public record SkillOption(String skillTag, String skillName, String description, Integer nodeCount,
                          Integer estimatedWeeks, double affinity) {
}
