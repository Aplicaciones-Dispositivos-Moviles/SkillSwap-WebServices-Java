package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReviewDeadlinePolicyRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory policies, keyed by plan. */
public class FakeReviewDeadlinePolicyRepository implements ReviewDeadlinePolicyRepository {

    private final Map<String, ReviewDeadlinePolicy> policies = new LinkedHashMap<>();

    public Map<String, ReviewDeadlinePolicy> policies() {
        return policies;
    }

    @Override
    public ReviewDeadlinePolicy save(ReviewDeadlinePolicy policy) {
        policies.put(policy.getPlan(), policy);
        return policy;
    }

    @Override
    public Optional<ReviewDeadlinePolicy> findByPlan(String plan) {
        return Optional.ofNullable(policies.get(plan));
    }
}
