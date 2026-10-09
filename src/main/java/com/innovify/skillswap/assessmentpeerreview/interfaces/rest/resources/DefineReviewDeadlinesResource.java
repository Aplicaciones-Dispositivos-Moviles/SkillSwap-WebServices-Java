package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/**
 * The deadlines a Verificador senior defines for the cases of each plan.
 *
 * @param premiumPlanHours     hours for the cases of the monthly plan, from 1 to 48
 * @param freePlanBusinessDays business days (Monday to Friday, Peru time) for the cases of the free plan, from 1 to 5
 */
public record DefineReviewDeadlinesResource(Integer premiumPlanHours, Integer freePlanBusinessDays) {
}
