package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The student declares a career goal in free text.
 *
 * @param studentId the student
 * @param rawText   the goal in their own words
 * @param advanced  whether the path is started with an advanced path unlock redeemed with SkillCredits, so it does
 *                  not count toward the limits of the plan
 * @param skillTag  optional: the skill the student chose among the options of the goal; when present the path is
 *                  built from it instead of interpreting the text (blank counts as absent)
 */
public record DeclareGoalCommand(int studentId, String rawText, boolean advanced, String skillTag) {

    /** A regular path, which counts toward the limits of the plan. */
    public DeclareGoalCommand(int studentId, String rawText) {
        this(studentId, rawText, false, null);
    }

    /** A path whose goal is interpreted from the text. */
    public DeclareGoalCommand(int studentId, String rawText, boolean advanced) {
        this(studentId, rawText, advanced, null);
    }
}
