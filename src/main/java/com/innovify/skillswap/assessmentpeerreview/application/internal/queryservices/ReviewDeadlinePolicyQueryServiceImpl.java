package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReviewDeadlinePolicyQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReviewDeadlinePoliciesQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReviewDeadlinePolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewDeadlinePolicyQueryServiceImpl implements ReviewDeadlinePolicyQueryService {

    private final ReviewDeadlinePolicyRepository policyRepository;

    public ReviewDeadlinePolicyQueryServiceImpl(ReviewDeadlinePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    public List<EffectiveReviewDeadline> handle(GetReviewDeadlinePoliciesQuery query) {
        return List.of(ReviewDeadlinePolicy.PREMIUM_PLAN, ReviewDeadlinePolicy.FREE_PLAN).stream()
                .map(plan -> new EffectiveReviewDeadline(plan, policyRepository.findByPlan(plan).orElse(null)))
                .toList();
    }
}
