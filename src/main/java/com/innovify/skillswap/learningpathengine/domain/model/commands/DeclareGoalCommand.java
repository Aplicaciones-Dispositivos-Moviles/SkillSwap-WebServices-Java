package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The student declares a career goal in free text.
 *
 * @param studentId the student
 * @param rawText   the goal in their own words
 * @param advanced  whether the path is started with an advanced path unlock redeemed with SkillCredits, so it does
 *                  not count toward the limits of the plan
 */
public record DeclareGoalCommand(int studentId, String rawText, boolean advanced) {

    /** A regular path, which counts toward the limits of the plan. */
    public DeclareGoalCommand(int studentId, String rawText) {
        this(studentId, rawText, false);
    }
}
