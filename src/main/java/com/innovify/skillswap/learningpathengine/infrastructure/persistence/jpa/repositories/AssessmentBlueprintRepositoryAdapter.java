package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link AssessmentBlueprintRepository} port on top of Spring Data JPA. */
@Repository
public class AssessmentBlueprintRepositoryAdapter implements AssessmentBlueprintRepository {

    private final AssessmentBlueprintJpaRepository jpaRepository;

    public AssessmentBlueprintRepositoryAdapter(AssessmentBlueprintJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AssessmentBlueprint save(AssessmentBlueprint blueprint) {
        return jpaRepository.saveAndFlush(blueprint);
    }

    @Override
    public Optional<AssessmentBlueprint> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<AssessmentBlueprint> findLatestByPathNodeId(int pathNodeId) {
        return jpaRepository.findFirstByPathNodeIdOrderByIdDesc(pathNodeId);
    }

    @Override
    public List<String> findQuestionTextsByPathNodeId(int pathNodeId) {
        return jpaRepository.findByPathNodeIdOrderByIdDesc(pathNodeId).stream()
                .flatMap(blueprint -> blueprint.getQuestions().stream())
                .map(Question::getQuestionString)
                .toList();
    }
}
