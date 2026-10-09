package com.innovify.skillswap.reputation.application.fakes;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeVerifierReliabilityRepository implements VerifierReliabilityRepository {

    private final List<VerifierReliability> items = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private RuntimeException saveFailure;

    public List<VerifierReliability> items() {
        return items;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public VerifierReliability save(VerifierReliability reliability) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (reliability.getId() == null) {
            ReflectionTestUtils.setField(reliability, "id", nextId++);
        }
        if (!items.contains(reliability)) {
            items.add(reliability);
        }
        return reliability;
    }

    @Override
    public Optional<VerifierReliability> findByVerifierUserId(int verifierUserId) {
        return items.stream().filter(r -> r.getVerifierUserId() == verifierUserId).findFirst();
    }

    @Override
    public List<VerifierReliability> findByVerifierUserIds(Collection<Integer> verifierUserIds) {
        return items.stream().filter(r -> verifierUserIds.contains(r.getVerifierUserId())).toList();
    }
}
