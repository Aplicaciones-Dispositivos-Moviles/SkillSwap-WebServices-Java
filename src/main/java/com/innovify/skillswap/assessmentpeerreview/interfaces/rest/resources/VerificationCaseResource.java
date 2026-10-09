package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;

/**
 * A verification case. The verifier who rejected it before an appeal is deliberately not part of it.
 *
 * @param id             the case
 * @param attemptId      the attempt that opened it
 * @param studentId      the student under review
 * @param verifierUserId the assigned verifier; null while it is pending
 * @param pathNodeId     the node the student wants to demonstrate
 * @param skillTag       the skill under review
 * @param status         Pending, Assigned or Resolved
 * @param decision       Approved or Rejected; null until resolved
 * @param rubricNotes    the notes of the verifier; null until resolved
 * @param evidenceUrl    the link to the student's repository or portfolio, if any
 * @param appealCount    how many times the student appealed the case (0 or 1)
 * @param openedAt       when the case was opened (UTC)
 * @param assignedAt     when it was assigned (UTC)
 * @param resolvedAt     when it was resolved (UTC)
 */
public record VerificationCaseResource(int id, int attemptId, int studentId, Integer verifierUserId,
                                       int pathNodeId, String skillTag, String status, String decision,
                                       String rubricNotes, String evidenceUrl, int appealCount, Instant openedAt,
                                       Instant assignedAt, Instant resolvedAt) {
}
