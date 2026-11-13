package com.innovify.skillswap.subscriptionbilling.domain.model.queries;

/** The limits of the plan a student is on right now. */
public record GetPlanLimitsByStudentIdQuery(int studentId) {
}
