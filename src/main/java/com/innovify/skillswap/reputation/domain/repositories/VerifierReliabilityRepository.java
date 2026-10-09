package com.innovify.skillswap.reputation.domain.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import java.util.Optional;

/** Persistence port of the {@link VerifierReliability} aggregate. */
public interface VerifierReliabilityRepository {

    /** Persists a new or updated reliability and flushes right away. */
    VerifierReliability save(VerifierReliability reliability);

    Optional<VerifierReliability> findByVerifierUserId(int verifierUserId);
}
