package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The student makes a paused path active again, if their plan allows another active path.
 *
 * @param pathId    the path
 * @param studentId the authenticated student, who must own it
 */
public record ResumeLearningPathCommand(int pathId, int studentId) {
}
