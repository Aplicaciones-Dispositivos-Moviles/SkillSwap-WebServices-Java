package com.innovify.skillswap.reputation.application.fakes;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeStudentEmployabilityScoreRepository implements StudentEmployabilityScoreRepository {

    private final List<StudentEmployabilityScore> items = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<StudentEmployabilityScore> items() {
        return items;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public StudentEmployabilityScore save(StudentEmployabilityScore score) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (score.getId() == null) {
            ReflectionTestUtils.setField(score, "id", nextId++);
        }
        if (!items.contains(score)) {
            items.add(score);
        }
        return score;
    }

    @Override
    public Optional<StudentEmployabilityScore> findByStudentId(int studentId) {
        return items.stream().filter(s -> s.getStudentId() == studentId).findFirst();
    }
}
