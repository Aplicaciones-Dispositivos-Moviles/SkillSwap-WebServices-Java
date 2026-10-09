package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The student pauses one of their active paths, for instance to resume another one within the limit of the plan.
 *
 * @param pathId    the path
 * @param studentId the authenticated student, who must own it
 */
public record PauseLearningPathCommand(int pathId, int studentId) {
}
