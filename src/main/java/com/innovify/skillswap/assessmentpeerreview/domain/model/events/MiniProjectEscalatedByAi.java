package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * The AI could not approve the delivery of a mini-project on its own and a case was opened for a verifier (US19). It
 * is not published when the plan had no escalations left (no case is opened then) or when the AI approved the
 * delivery.
 *
 * @param submissionId       the delivery
 * @param studentId          the student
 * @param pathNodeId         the node of the delivery
 * @param skillTag           the skill of the mini-project
 * @param verificationCaseId the case opened for a verifier
 */
public record MiniProjectEscalatedByAi(int submissionId, int studentId, int pathNodeId, String skillTag,
                                       int verificationCaseId) implements DomainEvent {
}
