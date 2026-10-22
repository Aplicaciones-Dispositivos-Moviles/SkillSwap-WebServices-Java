package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * The AI approved the delivery of a mini-project on its own and the node was completed with it, without a verifier
 * (US19). It is not published when the node was already completed, so a skill is never counted twice.
 *
 * @param submissionId the delivery
 * @param studentId    the student
 * @param pathNodeId   the node that was completed
 * @param skillTag     the skill demonstrated
 */
public record MiniProjectApprovedByAi(int submissionId, int studentId, int pathNodeId, String skillTag)
        implements DomainEvent {
}
