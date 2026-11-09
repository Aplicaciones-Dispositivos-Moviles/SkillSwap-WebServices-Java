package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;
import org.springframework.data.domain.Pageable;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "assessment_attempts" table. Only {@link AssessmentAttemptRepositoryAdapter} uses it. */
public interface AssessmentAttemptJpaRepository extends JpaRepository<AssessmentAttempt, Integer> {

    Optional<AssessmentAttempt> findByBlueprintId(int blueprintId);

    List<AssessmentAttempt> findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(Instant from, Instant to);

    List<AssessmentAttempt> findByPassedTrueAndStudentIdNotOrderByCompletedAtDescIdDesc(int studentId,
                                                                                       Pageable pageable);

    List<AssessmentAttempt> findByPassedTrueAndStudentIdNotAndSkillTagOrderByCompletedAtDescIdDesc(
            int studentId, String skillTag, Pageable pageable);
}
