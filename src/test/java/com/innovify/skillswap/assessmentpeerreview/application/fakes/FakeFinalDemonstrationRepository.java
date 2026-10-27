package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeFinalDemonstrationRepository implements FinalDemonstrationRepository {

    private final List<FinalDemonstration> demonstrations = new ArrayList<>();
    private final List<Integer> lockedDemonstrations = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<FinalDemonstration> demonstrations() {
        return demonstrations;
    }

    /** The demonstrations read with a lock, in order. */
    public List<Integer> lockedDemonstrations() {
        return lockedDemonstrations;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public FinalDemonstration save(FinalDemonstration demonstration) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (demonstration.getId() == null) {
            ReflectionTestUtils.setField(demonstration, "id", nextId++);
        }
        if (!demonstrations.contains(demonstration)) {
            demonstrations.add(demonstration);
        }
        return demonstration;
    }

    @Override
    public Optional<FinalDemonstration> findById(int id) {
        return demonstrations.stream().filter(d -> Objects.equals(d.getId(), id)).findFirst();
    }

    @Override
    public Optional<FinalDemonstration> findByIdForUpdate(int id) {
        lockedDemonstrations.add(id);
        return findById(id);
    }

    @Override
    public Optional<FinalDemonstration> findBlockingByLearningPathId(int learningPathId) {
        return demonstrations.stream()
                .filter(d -> d.getLearningPathId() == learningPathId && d.getStatus().blocksNewDemonstration())
                .findFirst();
    }

    @Override
    public List<FinalDemonstration> findByStudentId(int studentId) {
        return demonstrations.stream().filter(d -> d.getStudentId() == studentId)
                .sorted(Comparator.comparing(FinalDemonstration::getId).reversed()).toList();
    }

    @Override
    public List<FinalDemonstration> findByVerifierUserId(int verifierUserId) {
        return demonstrations.stream()
                .filter(d -> d.getVerifierUserId() == verifierUserId
                        && d.getStatus() != com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects
                        .DemonstrationStatus.CANCELLED)
                .sorted(Comparator.comparing(FinalDemonstration::getScheduledAt)).toList();
    }

    @Override
    public Map<Integer, Integer> countActiveByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (FinalDemonstration d : demonstrations) {
            boolean active = d.isScheduled() || d.getStatus() == com.innovify.skillswap.assessmentpeerreview.domain
                    .model.valueobjects.DemonstrationStatus.VIDEO_SUBMITTED;
            if (active && verifierUserIds.contains(d.getVerifierUserId())) {
                counts.merge(d.getVerifierUserId(), 1, Integer::sum);
            }
        }
        return counts;
    }
}
