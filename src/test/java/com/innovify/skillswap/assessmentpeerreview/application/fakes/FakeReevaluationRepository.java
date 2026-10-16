package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeReevaluationRepository implements ReevaluationRepository {

    private final List<Reevaluation> reevaluations = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<Reevaluation> reevaluations() {
        return reevaluations;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public Reevaluation save(Reevaluation reevaluation) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (reevaluation.getId() == null) {
            ReflectionTestUtils.setField(reevaluation, "id", nextId++);
        }
        if (!reevaluations.contains(reevaluation)) {
            reevaluations.add(reevaluation);
        }
        return reevaluation;
    }

    @Override
    public List<Reevaluation> findBySource(ReevaluationSourceType sourceType, int sourceId) {
        return reevaluations.stream()
                .filter(r -> r.getSourceType() == sourceType && r.getSourceId() == sourceId)
                .toList();
    }
}
