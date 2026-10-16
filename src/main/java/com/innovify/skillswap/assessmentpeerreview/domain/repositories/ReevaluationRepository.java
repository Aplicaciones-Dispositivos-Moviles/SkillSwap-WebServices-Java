package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import java.util.List;

/** Persistence port of the {@link Reevaluation} aggregate. */
public interface ReevaluationRepository {

    /** Persists a new reevaluation and flushes right away. */
    Reevaluation save(Reevaluation reevaluation);

    /** The reevaluations ordered for an attempt or a case, from the oldest to the newest. */
    List<Reevaluation> findBySource(ReevaluationSourceType sourceType, int sourceId);
}
