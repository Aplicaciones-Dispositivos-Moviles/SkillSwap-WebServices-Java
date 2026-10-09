package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the current plan of the student allows.
 *
 * @param maxActiveRoutes    learning paths that can be active at the same time (1 free, 3 monthly)
 * @param maxTotalRoutes     learning paths in total (3 free); null means no cap (monthly)
 * @param monthlyEscalations verification cases that can be opened in a calendar month (3 free, 10 monthly)
 * @param reviewDeadline     how long a verifier has to review each case (5 business days free, 48 hours monthly)
 */
public record PlanLimitsResource(int maxActiveRoutes, Integer maxTotalRoutes, int monthlyEscalations,
                                 ReviewDeadlineResource reviewDeadline) {

    /**
     * @param amount how many units
     * @param unit   Hours or BusinessDays (Monday to Friday)
     */
    public record ReviewDeadlineResource(int amount,
                                         @Schema(allowableValues = {"Hours", "BusinessDays"}) String unit) {
    }
}
