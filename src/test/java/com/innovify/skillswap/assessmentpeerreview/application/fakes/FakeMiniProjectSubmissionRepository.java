package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeMiniProjectSubmissionRepository implements MiniProjectSubmissionRepository {

    private final List<MiniProjectSubmission> submissions = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<MiniProjectSubmission> submissions() {
        return submissions;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public MiniProjectSubmission save(MiniProjectSubmission submission) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (submission.getId() == null) {
            ReflectionTestUtils.setField(submission, "id", nextId++);
        }
        if (!submissions.contains(submission)) {
            submissions.add(submission);
        }
        return submission;
    }

    @Override
    public Optional<MiniProjectSubmission> findById(int id) {
        return submissions.stream().filter(s -> Objects.equals(s.getId(), id)).findFirst();
    }

    @Override
    public Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId) {
        return submissions.stream().filter(s -> s.getMiniProjectId() == miniProjectId).findFirst();
    }
}
