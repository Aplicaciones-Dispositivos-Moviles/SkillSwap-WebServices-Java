package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * Brings the active paths of a student within the limit of their current plan (after a downgrade to the free
 * plan): the path with the most recent progress stays active and the others are paused. Nothing is deleted.
 *
 * @param studentId the student
 */
public record EnforcePlanLimitsCommand(int studentId) {
}
