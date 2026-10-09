package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A verifier resolved a verification case, approving or rejecting it.
 *
 * @param caseId         the case
 * @param studentId      the student who owns the case
 * @param verifierUserId the verifier who resolved it
 * @param pathNodeId     the node of the case
 * @param skillTag       the skill of the case
 * @param decision       the decision
 * @param overturnedVerifierUserId the verifier whose rejection this decision overturned after an appeal;
 *                       null when nobody was overturned
 */
public record VerificationCaseResolved(int caseId, int studentId, int verifierUserId, int pathNodeId,
                                       String skillTag, ReviewDecision decision,
                                       Integer overturnedVerifierUserId) implements DomainEvent {
}
