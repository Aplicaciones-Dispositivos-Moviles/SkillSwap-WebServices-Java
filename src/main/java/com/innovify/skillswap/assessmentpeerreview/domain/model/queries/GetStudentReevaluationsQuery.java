package com.innovify.skillswap.assessmentpeerreview.domain.model.queries;

/**
 * The reevaluations ordered on the results of a student, so they can read why a node of their path was reopened.
 *
 * @param studentId the student who asks; only their own reevaluations are returned
 */
public record GetStudentReevaluationsQuery(int studentId) {
}
