package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "reevaluations" table. Only {@link ReevaluationRepositoryAdapter} uses it. */
public interface ReevaluationJpaRepository extends JpaRepository<Reevaluation, Integer> {

    List<Reevaluation> findBySourceTypeAndSourceIdOrderByCreatedAtAscIdAsc(ReevaluationSourceType sourceType,
                                                                           int sourceId);
}
