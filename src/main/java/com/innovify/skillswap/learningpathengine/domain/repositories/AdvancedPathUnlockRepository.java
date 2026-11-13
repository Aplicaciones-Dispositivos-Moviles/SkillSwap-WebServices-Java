package com.innovify.skillswap.learningpathengine.domain.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import java.util.List;

/** Persistence port of the {@link AdvancedPathUnlock} aggregate. */
public interface AdvancedPathUnlockRepository {

    /** Persists a new or updated unlock and flushes right away. */
    AdvancedPathUnlock save(AdvancedPathUnlock unlock);

    /** The unlocks of the student, oldest first. */
    List<AdvancedPathUnlock> findByStudentId(int studentId);

    boolean existsByRedemptionId(int redemptionId);
}
