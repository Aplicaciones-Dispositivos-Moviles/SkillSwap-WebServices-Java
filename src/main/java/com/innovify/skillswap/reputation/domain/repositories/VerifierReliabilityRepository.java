package com.innovify.skillswap.reputation.domain.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link VerifierReliability} aggregate. */
public interface VerifierReliabilityRepository {

    /** Persists a new or updated reliability and flushes right away. */
    VerifierReliability save(VerifierReliability reliability);

    Optional<VerifierReliability> findByVerifierUserId(int verifierUserId);

    /** The reliabilities recorded for those verifiers; the ones without any are left out. */
    List<VerifierReliability> findByVerifierUserIds(Collection<Integer> verifierUserIds);
}
