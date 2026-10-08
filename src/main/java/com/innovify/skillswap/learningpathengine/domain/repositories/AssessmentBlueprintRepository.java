package com.innovify.skillswap.learningpathengine.domain.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import java.util.Optional;

/** Persistence port of the {@link AssessmentBlueprint} aggregate. */
public interface AssessmentBlueprintRepository {

    /** Persists a new blueprint and flushes right away. */
    AssessmentBlueprint save(AssessmentBlueprint blueprint);

    Optional<AssessmentBlueprint> findById(int id);

    /** The most recently generated blueprint of a node. */
    Optional<AssessmentBlueprint> findLatestByPathNodeId(int pathNodeId);
}
