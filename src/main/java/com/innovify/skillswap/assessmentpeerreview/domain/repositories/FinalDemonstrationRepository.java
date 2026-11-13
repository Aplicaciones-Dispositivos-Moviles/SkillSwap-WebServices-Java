package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port of the {@link FinalDemonstration} aggregate. */
public interface FinalDemonstrationRepository {

    /** Persists a new or updated demonstration and flushes right away. */
    FinalDemonstration save(FinalDemonstration demonstration);

    Optional<FinalDemonstration> findById(int id);

    /**
     * Same as {@link #findById(int)} but locks the row until the current transaction ends, so two changes of the
     * same demonstration do not overlap. It must run inside a transaction.
     */
    Optional<FinalDemonstration> findByIdForUpdate(int id);

    /** The demonstration of the path that still blocks a new one (scheduled, with video or approved), if any. */
    Optional<FinalDemonstration> findBlockingByLearningPathId(int learningPathId);

    /** The demonstrations of the student, the most recent first. */
    List<FinalDemonstration> findByStudentId(int studentId);

    /** The demonstrations assigned to the verifier that are not cancelled, the soonest first. */
    List<FinalDemonstration> findByVerifierUserId(int verifierUserId);

    /** The scheduled and video-submitted demonstrations of each verifier; verifiers without any are not in the map. */
    Map<Integer, Integer> countActiveByVerifierUserIds(Collection<Integer> verifierUserIds);
}
