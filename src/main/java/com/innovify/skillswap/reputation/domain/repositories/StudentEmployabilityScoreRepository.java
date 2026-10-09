package com.innovify.skillswap.reputation.domain.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import java.util.Optional;

/** Persistence port of the {@link StudentEmployabilityScore} aggregate. */
public interface StudentEmployabilityScoreRepository {

    /** Persists a new or updated score and flushes right away. */
    StudentEmployabilityScore save(StudentEmployabilityScore score);

    Optional<StudentEmployabilityScore> findByStudentId(int studentId);
}
