package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReviewDeadlinePoliciesQuery;
import java.util.List;

/** Review deadline policy query service interface. */
public interface ReviewDeadlinePolicyQueryService {

    /**
     * The deadline that applies now to each plan, monthly first: the one a senior defined, or the one of the plan
     * (with no author) while nobody defined it.
     */
    List<EffectiveReviewDeadline> handle(GetReviewDeadlinePoliciesQuery query);

    /**
     * The deadline of a plan.
     *
     * @param plan            Premium or Free
     * @param policy          the policy a senior defined; null while the plan keeps its own deadline
     */
    record EffectiveReviewDeadline(String plan, ReviewDeadlinePolicy policy) {

        public com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline deadline() {
            return policy == null ? ReviewDeadlinePolicy.defaultFor(plan) : policy.getDeadline();
        }
    }
}
