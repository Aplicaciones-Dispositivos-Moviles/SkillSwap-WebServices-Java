package com.innovify.skillswap.subscriptionbilling.application.internal.queryservices;

import com.innovify.skillswap.subscriptionbilling.application.queryservices.SubscriptionQueryService;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetCurrentSubscriptionByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetPlanLimitsByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;

public class SubscriptionQueryServiceImpl implements SubscriptionQueryService {

    private final SubscriptionRepository subscriptions;

    public SubscriptionQueryServiceImpl(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public Optional<Subscription> handle(GetCurrentSubscriptionByStudentIdQuery query) {
        return subscriptions.findCurrentByStudentId(query.studentId());
    }

    @Override
    public PlanLimits handle(GetPlanLimitsByStudentIdQuery query) {
        Instant now = Instant.now();
        boolean premium = subscriptions.findCurrentByStudentId(query.studentId())
                .filter(subscription -> subscription.grantsPremiumAt(now))
                .isPresent();
        return premium ? PlanLimits.PREMIUM : PlanLimits.FREE;
    }
}
