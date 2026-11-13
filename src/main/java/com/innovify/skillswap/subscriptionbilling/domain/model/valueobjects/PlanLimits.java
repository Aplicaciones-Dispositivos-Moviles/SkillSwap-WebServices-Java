package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * What a plan allows. No plan buys the approval of a certificate, and both allow the same number of attempts:
 * they only differ in how many learning paths a student keeps and how many cases they escalate to a verifier.
 *
 * @param plan               the plan
 * @param maxActiveRoutes    learning paths that can be active at the same time
 * @param maxTotalRoutes     learning paths in total, whatever their state; null means no cap
 * @param monthlyEscalations verification cases the student can open in a calendar month
 * @param reviewDeadline     how long a verifier has to review each of those cases
 */
public record PlanLimits(PlanType plan, int maxActiveRoutes, Integer maxTotalRoutes, int monthlyEscalations,
                         ReviewDeadline reviewDeadline) {

    /** S/ 0: 1 active path, 3 in total, 3 escalations a month reviewed in up to 5 business days. */
    public static final PlanLimits FREE = new PlanLimits(PlanType.FREE, 1, 3, 3, ReviewDeadline.businessDays(5));

    /** S/ 29.90 a month: 3 active paths, no total cap, 10 escalations a month reviewed in 48 hours. */
    public static final PlanLimits PREMIUM = new PlanLimits(PlanType.PREMIUM, 3, null, 10, ReviewDeadline.hours(48));

    public PlanLimits {
        if (plan == null) {
            throw new DomainException("The plan is required.");
        }
        if (maxActiveRoutes <= 0) {
            throw new DomainException("A plan must allow at least one active path.");
        }
        if (maxTotalRoutes != null && maxTotalRoutes < maxActiveRoutes) {
            throw new DomainException("The total paths cannot be fewer than the active ones.");
        }
        if (monthlyEscalations < 0) {
            throw new DomainException("The monthly escalations cannot be negative.");
        }
        if (reviewDeadline == null) {
            throw new DomainException("The review deadline is required.");
        }
    }

    public static PlanLimits of(PlanType plan) {
        return plan == PlanType.PREMIUM ? PREMIUM : FREE;
    }

    /** Whether the plan caps the total number of paths. */
    public boolean capsTotalRoutes() {
        return maxTotalRoutes != null;
    }
}
