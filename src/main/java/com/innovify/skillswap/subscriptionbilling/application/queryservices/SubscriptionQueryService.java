package com.innovify.skillswap.subscriptionbilling.application.queryservices;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetCurrentSubscriptionByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetPlanLimitsByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import java.util.Optional;

/** Subscription query service interface. */
public interface SubscriptionQueryService {

    /** The subscription that has not expired, or empty when the student has none. */
    Optional<Subscription> handle(GetCurrentSubscriptionByStudentIdQuery query);

    /**
     * The limits of the plan the student is on right now: the paid plan while a subscription grants it, the free
     * plan otherwise. A student never needs a subscription to be on the free plan.
     */
    PlanLimits handle(GetPlanLimitsByStudentIdQuery query);
}
