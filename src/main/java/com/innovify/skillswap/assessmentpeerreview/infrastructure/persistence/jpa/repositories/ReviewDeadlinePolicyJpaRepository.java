package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the "review_deadline_policies" table. Only {@link ReviewDeadlinePolicyRepositoryAdapter} uses
 * it.
 */
public interface ReviewDeadlinePolicyJpaRepository extends JpaRepository<ReviewDeadlinePolicy, String> {
}
