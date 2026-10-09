package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;

/**
 * A graded attempt. The correct answers are never part of it.
 *
 * @param id                   the attempt
 * @param blueprintId          the assessment that was answered
 * @param studentId            who answered
 * @param score                the number of correct answers
 * @param totalQuestions       the number of questions
 * @param passed               whether the attempt approved the node
 * @param completedAt          when it was graded (UTC)
 * @param verificationCaseId   the case opened by a failed attempt; null when it passed, and in a read of the
 *                             attempt by id
 * @param verificationCaseStatus Pending, Assigned or Resolved; null like the id
 */
public record AssessmentAttemptResource(int id, int blueprintId, int studentId, int score, int totalQuestions,
                                        boolean passed, Instant completedAt, Integer verificationCaseId,
                                        String verificationCaseStatus) {
}
