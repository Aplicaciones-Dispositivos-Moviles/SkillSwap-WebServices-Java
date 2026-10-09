package com.innovify.skillswap.moderationdisputes.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.domain.repositories.DisputeRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link DisputeRepository} port on top of Spring Data JPA. */
@Repository
public class DisputeRepositoryAdapter implements DisputeRepository {

    private final DisputeJpaRepository jpaRepository;

    public DisputeRepositoryAdapter(DisputeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Dispute save(Dispute dispute) {
        return jpaRepository.saveAndFlush(dispute);
    }

    @Override
    public Optional<Dispute> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Dispute> findByIdForUpdate(int id) {
        return jpaRepository.findForUpdateById(id);
    }

    @Override
    public Optional<Dispute> findBySource(DisputeSourceType sourceType, int sourceReferenceId) {
        return jpaRepository.findFirstBySourceTypeAndSourceReferenceId(sourceType, sourceReferenceId);
    }

    @Override
    public List<Dispute> findByAssignedVerifier(int verifierUserId, DisputeStatus status) {
        return status == null
                ? jpaRepository.findByAssignedVerifierUserIdOrderByIdAsc(verifierUserId)
                : jpaRepository.findByAssignedVerifierUserIdAndStatusOrderByIdAsc(verifierUserId, status);
    }

    @Override
    public List<Integer> findUnassignedPendingIds() {
        return jpaRepository.findUnassignedIdsByStatus(DisputeStatus.PENDING);
    }

    @Override
    public Map<Integer, Integer> countPendingByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Map<Integer, Integer> counts = new HashMap<>();
        if (verifierUserIds.isEmpty()) {
            return counts;
        }
        // The pending disputes of a handful of reviewers are few, so they are counted here.
        for (Dispute pending : jpaRepository.findByAssignedVerifierUserIdInAndStatus(Set.copyOf(verifierUserIds),
                DisputeStatus.PENDING)) {
            counts.merge(pending.getAssignedVerifierUserId(), 1, Integer::sum);
        }
        return counts;
    }
}
