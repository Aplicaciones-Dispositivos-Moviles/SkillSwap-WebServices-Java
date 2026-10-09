package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeAssessmentAttemptRepository implements AssessmentAttemptRepository {

    private final List<AssessmentAttempt> attempts = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<AssessmentAttempt> attempts() {
        return attempts;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public AssessmentAttempt save(AssessmentAttempt attempt) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (attempt.getId() == null) {
            ReflectionTestUtils.setField(attempt, "id", nextId++);
        }
        if (!attempts.contains(attempt)) {
            attempts.add(attempt);
        }
        return attempt;
    }

    @Override
    public Optional<AssessmentAttempt> findById(int id) {
        return attempts.stream().filter(a -> a.getId() == id).findFirst();
    }

    @Override
    public Optional<AssessmentAttempt> findByBlueprintId(int blueprintId) {
        return attempts.stream().filter(a -> a.getBlueprintId() == blueprintId).findFirst();
    }
}
