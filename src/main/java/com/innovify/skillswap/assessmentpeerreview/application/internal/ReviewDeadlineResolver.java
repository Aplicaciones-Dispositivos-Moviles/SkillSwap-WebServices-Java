package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReviewDeadlinePolicyRepository;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
import org.springframework.stereotype.Service;

/**
 * Which deadline a case gets: the one a Verificador senior defined for the plan of the student, or, while none was
 * defined, the one of the plan itself (48 hours monthly, 5 business days free).
 */
@Service
public class ReviewDeadlineResolver {

    private final ReviewDeadlinePolicyRepository policyRepository;
    private final SubscriptionContextFacade subscriptionFacade;

    public ReviewDeadlineResolver(ReviewDeadlinePolicyRepository policyRepository,
                                  SubscriptionContextFacade subscriptionFacade) {
        this.policyRepository = policyRepository;
        this.subscriptionFacade = subscriptionFacade;
    }

    /** The deadline for a case opened now under those limits. */
    public ReviewDeadline forPlan(PlanLimitsView limits) {
        return policyRepository.findByPlan(limits.plan())
                .map(ReviewDeadlinePolicy::getDeadline)
                .orElseGet(() -> limits.reviewDeadlineHours() != null
                        ? ReviewDeadline.hours(limits.reviewDeadlineHours())
                        : ReviewDeadline.businessDays(limits.reviewDeadlineBusinessDays()));
    }

    /** The deadline for a case of the student opened now, by their current plan. */
    public ReviewDeadline forStudent(int studentId) {
        return forPlan(subscriptionFacade.getPlanLimits(studentId));
    }
}
