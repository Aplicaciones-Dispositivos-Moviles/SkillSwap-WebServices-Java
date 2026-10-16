package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import java.util.Optional;

/** Persistence port of the {@link MiniProjectSubmission} aggregate. */
public interface MiniProjectSubmissionRepository {

    /** Persists a new delivery and flushes right away. */
    MiniProjectSubmission save(MiniProjectSubmission submission);

    Optional<MiniProjectSubmission> findById(int id);

    /** The delivery of a mini-project, if it was delivered (a mini-project is delivered once). */
    Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId);
}
