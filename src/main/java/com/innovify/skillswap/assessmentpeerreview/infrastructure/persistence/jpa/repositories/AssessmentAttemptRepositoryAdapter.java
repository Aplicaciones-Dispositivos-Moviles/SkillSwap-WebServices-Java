package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link AssessmentAttemptRepository} port on top of Spring Data JPA. */
@Repository
public class AssessmentAttemptRepositoryAdapter implements AssessmentAttemptRepository {

    private final AssessmentAttemptJpaRepository jpaRepository;

    public AssessmentAttemptRepositoryAdapter(AssessmentAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AssessmentAttempt save(AssessmentAttempt attempt) {
        return jpaRepository.saveAndFlush(attempt);
    }

    @Override
    public Optional<AssessmentAttempt> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<AssessmentAttempt> findByBlueprintId(int blueprintId) {
        return jpaRepository.findByBlueprintId(blueprintId);
    }
}
