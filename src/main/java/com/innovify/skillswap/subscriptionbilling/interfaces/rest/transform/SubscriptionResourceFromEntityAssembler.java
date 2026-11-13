package com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.PlanLimitsResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.StudentPlanResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.SubscriptionResource;

/** Converts the Subscription &amp; Billing model into REST resources. */
public final class SubscriptionResourceFromEntityAssembler {

    private SubscriptionResourceFromEntityAssembler() {
    }

    public static SubscriptionResource toResourceFromEntity(Subscription entity) {
        SubscriptionPlan plan = entity.getPlan();
        return new SubscriptionResource(
                entity.getId(),
                entity.getStudentId(),
                plan.name(),
                plan.productId(),
                plan.price().amount(),
                plan.price().currency(),
                entity.getStatus().value(),
                entity.getStoreTransactionId(),
                entity.getStartedAt(),
                entity.getCurrentPeriodEnd(),
                entity.getCancelledAt(),
                entity.getExpiredAt());
    }

    public static PlanLimitsResource toResource(PlanLimits limits) {
        return new PlanLimitsResource(limits.maxActiveRoutes(), limits.maxTotalRoutes(), limits.monthlyEscalations(),
                new PlanLimitsResource.ReviewDeadlineResource(limits.reviewDeadline().amount(),
                        limits.reviewDeadline().unit().value()));
    }

    /** @param current the subscription that has not expired, or null */
    public static StudentPlanResource toResource(PlanLimits limits, Subscription current) {
        return new StudentPlanResource(limits.plan().value(), toResource(limits),
                current == null ? null : toResourceFromEntity(current));
    }
}
