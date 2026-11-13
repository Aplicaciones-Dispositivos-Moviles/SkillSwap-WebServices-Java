package com.innovify.skillswap.learningpathengine.domain.model.queries;

/**
 * The skills a student could choose for a goal written in free text.
 *
 * @param studentId the student
 * @param goal      the goal in their own words
 */
public record GetSkillOptionsQuery(int studentId, String goal) {
}
