package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "mini_project_submissions" table. Only {@link MiniProjectSubmissionRepositoryAdapter} uses it. */
public interface MiniProjectSubmissionJpaRepository extends JpaRepository<MiniProjectSubmission, Integer> {

    Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId);
}
