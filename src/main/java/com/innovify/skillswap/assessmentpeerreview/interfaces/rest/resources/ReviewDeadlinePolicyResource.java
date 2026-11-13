package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The deadline that applies to the cases opened now for the students of a plan.
 *
 * @param plan            Premium (monthly) or Free
 * @param amount          how many units
 * @param unit            Hours or BusinessDays
 * @param definedBySenior whether a Verificador senior defined it; false while the plan keeps its own deadline
 * @param updatedByUserId the senior who defined it; null when not defined
 * @param updatedAt       when it was defined (UTC); null when not defined
 */
public record ReviewDeadlinePolicyResource(@Schema(allowableValues = {"Premium", "Free"}) String plan, int amount,
                                           @Schema(allowableValues = {"Hours", "BusinessDays"}) String unit,
                                           boolean definedBySenior, Integer updatedByUserId, Instant updatedAt) {
}
