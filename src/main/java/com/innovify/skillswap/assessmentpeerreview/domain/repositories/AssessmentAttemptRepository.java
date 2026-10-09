package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import java.util.Optional;

/** Persistence port of the {@link AssessmentAttempt} aggregate. */
public interface AssessmentAttemptRepository {

    /** Persists the attempt and flushes right away. */
    AssessmentAttempt save(AssessmentAttempt attempt);

    Optional<AssessmentAttempt> findById(int id);

    /** The attempt made at the blueprint; there is at most one. */
    Optional<AssessmentAttempt> findByBlueprintId(int blueprintId);
}
