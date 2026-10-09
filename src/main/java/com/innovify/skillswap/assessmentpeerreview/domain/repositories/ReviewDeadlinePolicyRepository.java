package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import java.util.Optional;

/** Persistence port of the {@link ReviewDeadlinePolicy} aggregate. */
public interface ReviewDeadlinePolicyRepository {

    /** Persists a new or updated policy and flushes right away. */
    ReviewDeadlinePolicy save(ReviewDeadlinePolicy policy);

    /** The deadline a senior defined for the plan (Free or Premium), if any. */
    Optional<ReviewDeadlinePolicy> findByPlan(String plan);
}
