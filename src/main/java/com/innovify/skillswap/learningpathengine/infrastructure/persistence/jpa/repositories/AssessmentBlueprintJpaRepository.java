package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "assessment_blueprints" table. Only {@link AssessmentBlueprintRepositoryAdapter} uses it. */
public interface AssessmentBlueprintJpaRepository extends JpaRepository<AssessmentBlueprint, Integer> {

    Optional<AssessmentBlueprint> findFirstByPathNodeIdOrderByIdDesc(int pathNodeId);

    List<AssessmentBlueprint> findByPathNodeIdOrderByIdDesc(int pathNodeId);
}
