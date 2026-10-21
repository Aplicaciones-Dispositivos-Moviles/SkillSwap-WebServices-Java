package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import java.util.Optional;

/** Persistence port of the {@link MiniProjectSubmission} aggregate. */
public interface MiniProjectSubmissionRepository {

    /** Persists a new or updated delivery and flushes right away. */
    MiniProjectSubmission save(MiniProjectSubmission submission);

    Optional<MiniProjectSubmission> findById(int id);

    /**
     * Same as {@link #findById(int)} but locks the row until the current transaction ends, so the preliminary
     * evaluation of a delivery is recorded once even if it runs twice at the same time. It must run inside a
     * transaction.
     */
    Optional<MiniProjectSubmission> findByIdForUpdate(int id);

    /** The delivery of a mini-project, if it was delivered (a mini-project is delivered once). */
    Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId);
}
