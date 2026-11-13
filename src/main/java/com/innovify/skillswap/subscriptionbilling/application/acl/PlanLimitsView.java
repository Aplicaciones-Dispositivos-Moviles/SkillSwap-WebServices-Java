package com.innovify.skillswap.subscriptionbilling.application.acl;

/**
 * The limits of the plan a student is on right now, as other bounded contexts consume them. Exactly one of the two
 * review deadlines is set.
 *
 * @param plan                       Free or Premium
 * @param maxActiveRoutes            learning paths that can be active at the same time
 * @param maxTotalRoutes             learning paths in total, whatever their state; null means no cap
 * @param monthlyEscalations         verification cases the student can open in a calendar month
 * @param reviewDeadlineHours        hours a verifier has to review a case, or null
 * @param reviewDeadlineBusinessDays business days (Monday to Friday) a verifier has to review a case, or null
 */
public record PlanLimitsView(String plan, int maxActiveRoutes, Integer maxTotalRoutes, int monthlyEscalations,
                             Integer reviewDeadlineHours, Integer reviewDeadlineBusinessDays) {

    public boolean isFree() {
        return "Free".equals(plan);
    }
}
