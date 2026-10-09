package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The plan a student is on right now, with its limits.
 *
 * @param plan         Free or Premium (the monthly plan)
 * @param limits       what the plan allows
 * @param subscription the subscription that has not expired, or null on the free plan
 */
public record StudentPlanResource(@Schema(allowableValues = {"Free", "Premium"}) String plan,
                                  PlanLimitsResource limits, SubscriptionResource subscription) {
}
