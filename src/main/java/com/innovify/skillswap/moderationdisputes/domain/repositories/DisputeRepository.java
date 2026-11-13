package com.innovify.skillswap.moderationdisputes.domain.repositories;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port of the {@link Dispute} aggregate. */
public interface DisputeRepository {

    /** Persists a new or updated dispute and flushes right away. */
    Dispute save(Dispute dispute);

    Optional<Dispute> findById(int id);

    /**
     * Same as {@link #findById(int)} but locks the row until the current transaction ends, so two requests cannot
     * resolve or assign the same dispute at once. It must run inside a transaction.
     */
    Optional<Dispute> findByIdForUpdate(int id);

    /** The dispute opened for that source, if any. */
    Optional<Dispute> findBySource(DisputeSourceType sourceType, int sourceReferenceId);

    /** The disputes assigned to the reviewer in that state (all of them when null), oldest first. */
    List<Dispute> findByAssignedVerifier(int verifierUserId, DisputeStatus status);

    /** The ids of the pending disputes that still have no reviewer, oldest first. */
    List<Integer> findUnassignedPendingIds();

    /** The pending disputes of each reviewer; reviewers without any are not in the map. */
    Map<Integer, Integer> countPendingByVerifierUserIds(Collection<Integer> verifierUserIds);
}
