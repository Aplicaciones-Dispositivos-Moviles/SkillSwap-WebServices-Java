package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A student passed the assessment of a node automatically.
 *
 * @param attemptId  the attempt
 * @param studentId  the student
 * @param pathNodeId the node that was completed
 * @param skillTag   the skill demonstrated
 */
public record AssessmentAttemptPassed(int attemptId, int studentId, int pathNodeId, String skillTag)
        implements DomainEvent {
}
