package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link VerifierReliabilityRepository} port on top of Spring Data JPA. */
@Repository
public class VerifierReliabilityRepositoryAdapter implements VerifierReliabilityRepository {

    private final VerifierReliabilityJpaRepository jpaRepository;

    public VerifierReliabilityRepositoryAdapter(VerifierReliabilityJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public VerifierReliability save(VerifierReliability reliability) {
        return jpaRepository.saveAndFlush(reliability);
    }

    @Override
    public Optional<VerifierReliability> findByVerifierUserId(int verifierUserId) {
        return jpaRepository.findByVerifierUserId(verifierUserId);
    }

    @Override
    public List<VerifierReliability> findByVerifierUserIds(Collection<Integer> verifierUserIds) {
        if (verifierUserIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByVerifierUserIdIn(verifierUserIds);
    }
}
