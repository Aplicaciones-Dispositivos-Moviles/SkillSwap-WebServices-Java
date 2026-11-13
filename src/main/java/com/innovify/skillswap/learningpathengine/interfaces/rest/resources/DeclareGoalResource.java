package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * Resource for declaring a career goal in free text.
 *
 * @param goal     the goal in the student's own words (1 to 500 characters)
 * @param advanced optional: true to start it as the advanced path redeemed with SkillCredits, which does not count
 *                 toward the limits of the plan and spends an available advanced path unlock
 * @param skillTag optional: the skill of the catalog the student chose among the skill options; when present the path
 *                 is built for it and the text of the goal is not interpreted again (422 SkillNotInCatalog if it is
 *                 not in the catalog; blank counts as absent)
 */
public record DeclareGoalResource(String goal, Boolean advanced, String skillTag) {

    public DeclareGoalResource(String goal) {
        this(goal, null, null);
    }

    public DeclareGoalResource(String goal, Boolean advanced) {
        this(goal, advanced, null);
    }
}
