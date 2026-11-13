package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which limit of the plan a request reached, sent with PLAN_LIMIT_REACHED so the app can show the "Alcanzaste el
 * límite de tu plan" screen with the right text.
 *
 * @param limit   ActiveRoutes or TotalRoutes
 * @param plan    Free or Premium
 * @param max     what the plan allows
 * @param current what the student has
 */
record PlanLimitBreach(String limit, String plan, int max, int current) {

    static final String ACTIVE_ROUTES = "ActiveRoutes";
    static final String TOTAL_ROUTES = "TotalRoutes";

    static PlanLimitBreach activeRoutes(PlanLimitsView limits, int current) {
        return new PlanLimitBreach(ACTIVE_ROUTES, limits.plan(), limits.maxActiveRoutes(), current);
    }

    static PlanLimitBreach totalRoutes(PlanLimitsView limits, int current) {
        return new PlanLimitBreach(TOTAL_ROUTES, limits.plan(), limits.maxTotalRoutes(), current);
    }

    /** The extra members of the error response; upgradeAvailable tells whether the paid plan would lift it. */
    Map<String, Object> toDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("limit", limit);
        details.put("plan", plan);
        details.put("max", max);
        details.put("current", current);
        details.put("upgradeAvailable", "Free".equals(plan));
        return details;
    }
}
