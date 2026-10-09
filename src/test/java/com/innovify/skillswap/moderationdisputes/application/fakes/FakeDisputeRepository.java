package com.innovify.skillswap.moderationdisputes.application.fakes;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.domain.repositories.DisputeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeDisputeRepository implements DisputeRepository {

    private final List<Dispute> disputes = new ArrayList<>();
    private final List<Integer> locked = new ArrayList<>();
    private int nextId = 1;

    public List<Dispute> disputes() {
        return disputes;
    }

    /** The disputes read with a lock, in order. */
    public List<Integer> locked() {
        return locked;
    }

    @Override
    public Dispute save(Dispute dispute) {
        if (dispute.getId() == null) {
            ReflectionTestUtils.setField(dispute, "id", nextId++);
        }
        if (!disputes.contains(dispute)) {
            disputes.add(dispute);
        }
        return dispute;
    }

    @Override
    public Optional<Dispute> findById(int id) {
        return disputes.stream().filter(d -> d.getId() == id).findFirst();
    }

    @Override
    public Optional<Dispute> findByIdForUpdate(int id) {
        locked.add(id);
        return findById(id);
    }

    @Override
    public Optional<Dispute> findBySource(DisputeSourceType sourceType, int sourceReferenceId) {
        return disputes.stream()
                .filter(d -> d.getSourceType() == sourceType && d.getSourceReferenceId() == sourceReferenceId)
                .findFirst();
    }

    @Override
    public List<Dispute> findByAssignedVerifier(int verifierUserId, DisputeStatus status) {
        return disputes.stream()
                .filter(d -> d.isAssignedTo(verifierUserId) && (status == null || d.getStatus() == status))
                .sorted(Comparator.comparing(Dispute::getId))
                .toList();
    }

    @Override
    public List<Integer> findUnassignedPendingIds() {
        return disputes.stream().filter(d -> d.isPending() && !d.hasReviewer()).map(Dispute::getId).sorted().toList();
    }

    @Override
    public Map<Integer, Integer> countPendingByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Map<Integer, Integer> counts = new HashMap<>();
        disputes.stream()
                .filter(d -> d.isPending() && d.hasReviewer() && verifierUserIds.contains(d.getAssignedVerifierUserId()))
                .forEach(d -> counts.merge(d.getAssignedVerifierUserId(), 1, Integer::sum));
        return counts;
    }
}
