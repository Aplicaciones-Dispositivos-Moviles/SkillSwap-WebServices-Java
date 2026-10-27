package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link FinalDemonstrationRepository} port on top of Spring Data JPA. */
@Repository
public class FinalDemonstrationRepositoryAdapter implements FinalDemonstrationRepository {

    private static final List<DemonstrationStatus> BLOCKING = Arrays.stream(DemonstrationStatus.values())
            .filter(DemonstrationStatus::blocksNewDemonstration)
            .toList();

    private static final List<DemonstrationStatus> ACTIVE = List.of(DemonstrationStatus.SCHEDULED,
            DemonstrationStatus.VIDEO_SUBMITTED);

    private final FinalDemonstrationJpaRepository jpaRepository;

    public FinalDemonstrationRepositoryAdapter(FinalDemonstrationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FinalDemonstration save(FinalDemonstration demonstration) {
        return jpaRepository.saveAndFlush(demonstration);
    }

    @Override
    public Optional<FinalDemonstration> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<FinalDemonstration> findByIdForUpdate(int id) {
        return jpaRepository.findForUpdateById(id);
    }

    @Override
    public Optional<FinalDemonstration> findBlockingByLearningPathId(int learningPathId) {
        return jpaRepository.findByLearningPathIdAndStatusIn(learningPathId, BLOCKING).stream().findFirst();
    }

    @Override
    public List<FinalDemonstration> findByStudentId(int studentId) {
        return jpaRepository.findByStudentIdOrderByCreatedAtDescIdDesc(studentId);
    }

    @Override
    public List<FinalDemonstration> findByVerifierUserId(int verifierUserId) {
        return jpaRepository.findByVerifierUserIdAndStatusNotOrderByScheduledAtAscIdAsc(verifierUserId,
                DemonstrationStatus.CANCELLED);
    }

    @Override
    public Map<Integer, Integer> countActiveByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Set<Integer> ids = new HashSet<>(verifierUserIds);
        Map<Integer, Integer> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }

        // The active demonstrations of a handful of verifiers are few, so they are counted here.
        for (FinalDemonstration active : jpaRepository.findByVerifierUserIdInAndStatusIn(ids, ACTIVE)) {
            counts.merge(active.getVerifierUserId(), 1, Integer::sum);
        }
        return counts;
    }
}
