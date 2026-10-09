package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "student_employability_scores" table. Only {@link StudentEmployabilityScoreRepositoryAdapter} uses it. */
public interface StudentEmployabilityScoreJpaRepository extends JpaRepository<StudentEmployabilityScore, Integer> {

    Optional<StudentEmployabilityScore> findByStudentId(int studentId);
}
