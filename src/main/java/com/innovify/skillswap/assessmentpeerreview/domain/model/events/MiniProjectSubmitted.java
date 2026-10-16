package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A student delivered the repository of the mini-project of a node. The preliminary AI evaluation and the opening of the
 * verification case react to it.
 *
 * @param submissionId   the delivery
 * @param miniProjectId  the mini-project that was delivered
 * @param studentId      the student
 * @param pathNodeId     the node the mini-project assesses
 * @param skillTag       the skill being demonstrated
 * @param repositoryUrl  the link to the repository
 * @param submittedAt    when it was delivered (UTC)
 */
public record MiniProjectSubmitted(int submissionId, int miniProjectId, int studentId, int pathNodeId,
                                   String skillTag, String repositoryUrl, Instant submittedAt)
        implements DomainEvent {
}
