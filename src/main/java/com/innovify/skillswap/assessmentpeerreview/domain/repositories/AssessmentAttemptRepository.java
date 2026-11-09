package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link AssessmentAttempt} aggregate. */
public interface AssessmentAttemptRepository {

    /** Persists the attempt and flushes right away. */
    AssessmentAttempt save(AssessmentAttempt attempt);

    Optional<AssessmentAttempt> findById(int id);

    /** The attempt made at the blueprint; there is at most one. */
    Optional<AssessmentAttempt> findByBlueprintId(int blueprintId);

    /** The attempts completed from {@code from} (inclusive) until {@code to} (exclusive), whatever their result. */
    List<AssessmentAttempt> findCompletedBetween(Instant from, Instant to);

    /**
     * The passed attempts of the students other than the given one, newest first, at most {@code limit}.
     *
     * @param skillTag only the attempts of that skill; null for all
     */
    List<AssessmentAttempt> findRecentPassedExcludingStudent(int excludedStudentId, String skillTag, int limit);
}
