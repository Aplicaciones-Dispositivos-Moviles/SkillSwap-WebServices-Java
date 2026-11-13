package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Collection;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link ReevaluationRepository} port on top of Spring Data JPA. */
@Repository
public class ReevaluationRepositoryAdapter implements ReevaluationRepository {

    private final ReevaluationJpaRepository jpaRepository;

    public ReevaluationRepositoryAdapter(ReevaluationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reevaluation save(Reevaluation reevaluation) {
        return jpaRepository.saveAndFlush(reevaluation);
    }

    @Override
    public List<Reevaluation> findBySource(ReevaluationSourceType sourceType, int sourceId) {
        return jpaRepository.findBySourceTypeAndSourceIdOrderByCreatedAtAscIdAsc(sourceType, sourceId);
    }

    @Override
    public Set<Integer> findReevaluatedSourceIds(ReevaluationSourceType sourceType, Collection<Integer> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findBySourceTypeAndSourceIdIn(sourceType, sourceIds).stream()
                .map(Reevaluation::getSourceId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
