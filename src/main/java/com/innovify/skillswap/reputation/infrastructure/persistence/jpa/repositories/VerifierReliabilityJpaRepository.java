package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "verifier_reliabilities" table. Only {@link VerifierReliabilityRepositoryAdapter} uses it. */
public interface VerifierReliabilityJpaRepository extends JpaRepository<VerifierReliability, Integer> {

    Optional<VerifierReliability> findByVerifierUserId(int verifierUserId);
}
