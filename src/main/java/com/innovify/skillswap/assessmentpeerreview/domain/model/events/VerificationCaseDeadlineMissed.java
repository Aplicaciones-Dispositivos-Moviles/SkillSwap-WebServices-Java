package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * The assigned verifier did not resolve a case within its deadline. It is published once per assignment, and
 * Reputation records the breach in the reliability of that verifier.
 *
 * @param caseId            the case
 * @param verifierUserId    the verifier who missed the deadline
 * @param studentId         the student of the case
 * @param reviewDueAt       the deadline that passed
 * @param newVerifierUserId the verifier the case was reassigned to; null when nobody else was available yet
 */
public record VerificationCaseDeadlineMissed(int caseId, int verifierUserId, int studentId, Instant reviewDueAt,
                                             Integer newVerifierUserId) implements DomainEvent {
}
