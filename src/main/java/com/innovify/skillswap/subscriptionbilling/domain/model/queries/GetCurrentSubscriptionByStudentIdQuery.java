package com.innovify.skillswap.subscriptionbilling.domain.model.queries;

/** The subscription of a student that has not expired, if any. */
public record GetCurrentSubscriptionByStudentIdQuery(int studentId) {
}
