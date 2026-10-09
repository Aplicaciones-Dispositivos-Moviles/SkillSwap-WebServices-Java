package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A limit of the plan that stopped part of the request, for the "Alcanzaste el límite de tu plan" screen.
 *
 * @param limit            MonthlyEscalations: the cases escalated to a verifier this calendar month
 * @param plan             Free or Premium
 * @param max              what the plan allows (3 free, 10 monthly)
 * @param current          what the student already used
 * @param upgradeAvailable whether the monthly plan would allow more
 */
public record PlanLimitReachedResource(@Schema(allowableValues = {"MonthlyEscalations"}) String limit,
                                       @Schema(allowableValues = {"Free", "Premium"}) String plan,
                                       int max, int current, boolean upgradeAvailable) {
}
