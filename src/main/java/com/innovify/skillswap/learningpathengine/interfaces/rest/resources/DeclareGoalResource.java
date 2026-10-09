package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * Resource for declaring a career goal in free text.
 *
 * @param goal     the goal in the student's own words (1 to 500 characters)
 * @param advanced optional: true to start it as the advanced path redeemed with SkillCredits, which does not count
 *                 toward the limits of the plan and spends an available advanced path unlock
 */
public record DeclareGoalResource(String goal, Boolean advanced) {

    public DeclareGoalResource(String goal) {
        this(goal, null);
    }
}
