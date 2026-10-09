package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "assessment_attempts" table. Only {@link AssessmentAttemptRepositoryAdapter} uses it. */
public interface AssessmentAttemptJpaRepository extends JpaRepository<AssessmentAttempt, Integer> {

    Optional<AssessmentAttempt> findByBlueprintId(int blueprintId);
}
