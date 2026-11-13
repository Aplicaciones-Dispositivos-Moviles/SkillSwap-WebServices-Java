package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReviewDeadlinePolicyRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link ReviewDeadlinePolicyRepository} port on top of Spring Data JPA. */
@Repository
public class ReviewDeadlinePolicyRepositoryAdapter implements ReviewDeadlinePolicyRepository {

    private final ReviewDeadlinePolicyJpaRepository jpaRepository;

    public ReviewDeadlinePolicyRepositoryAdapter(ReviewDeadlinePolicyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReviewDeadlinePolicy save(ReviewDeadlinePolicy policy) {
        return jpaRepository.saveAndFlush(policy);
    }

    @Override
    public Optional<ReviewDeadlinePolicy> findByPlan(String plan) {
        return plan == null ? Optional.empty() : jpaRepository.findById(plan);
    }
}
