package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import com.innovify.skillswap.learningpathengine.domain.repositories.AdvancedPathUnlockRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeAdvancedPathUnlockRepository implements AdvancedPathUnlockRepository {

    private final List<AdvancedPathUnlock> unlocks = new ArrayList<>();
    private int nextId = 1;

    public List<AdvancedPathUnlock> unlocks() {
        return unlocks;
    }

    @Override
    public AdvancedPathUnlock save(AdvancedPathUnlock unlock) {
        if (unlock.getId() == null) {
            ReflectionTestUtils.setField(unlock, "id", nextId++);
        }
        if (!unlocks.contains(unlock)) {
            unlocks.add(unlock);
        }
        return unlock;
    }

    @Override
    public List<AdvancedPathUnlock> findByStudentId(int studentId) {
        return unlocks.stream().filter(u -> u.getStudentId() == studentId)
                .sorted(Comparator.comparing(AdvancedPathUnlock::getId)).toList();
    }

    @Override
    public boolean existsByRedemptionId(int redemptionId) {
        return unlocks.stream().anyMatch(u -> u.getRedemptionId() == redemptionId);
    }
}
