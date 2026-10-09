package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A Verificador senior defines how long verifiers have to resolve the cases of each plan (US39). A null value means
 * the request did not carry it, which the service rejects as InvalidReviewDeadline.
 *
 * @param seniorUserId         the senior (the authenticated user)
 * @param premiumPlanHours     hours for the cases of the monthly plan, from 1 to 48
 * @param freePlanBusinessDays business days for the cases of the free plan, from 1 to 5
 */
public record DefineReviewDeadlinesCommand(int seniorUserId, Integer premiumPlanHours,
                                           Integer freePlanBusinessDays) {
}
