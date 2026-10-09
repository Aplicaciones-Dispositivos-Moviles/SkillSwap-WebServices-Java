package com.innovify.skillswap.subscriptionbilling.application.acl;

import com.innovify.skillswap.subscriptionbilling.application.queryservices.SubscriptionQueryService;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetPlanLimitsByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.ReviewDeadline;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionContextFacadeImpl implements SubscriptionContextFacade {

    private final SubscriptionQueryService queryService;

    public SubscriptionContextFacadeImpl(SubscriptionQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public PlanLimitsView getPlanLimits(int studentId) {
        PlanLimits limits = queryService.handle(new GetPlanLimitsByStudentIdQuery(studentId));
        ReviewDeadline deadline = limits.reviewDeadline();
        boolean inHours = deadline.unit() == ReviewDeadline.Unit.HOURS;
        return new PlanLimitsView(limits.plan().value(), limits.maxActiveRoutes(), limits.maxTotalRoutes(),
                limits.monthlyEscalations(), inHours ? deadline.amount() : null, inHours ? null : deadline.amount());
    }
}
