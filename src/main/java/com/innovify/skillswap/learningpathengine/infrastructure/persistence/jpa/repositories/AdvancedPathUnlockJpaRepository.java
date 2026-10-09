package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "advanced_path_unlocks" table. Only {@link AdvancedPathUnlockRepositoryAdapter} uses it. */
public interface AdvancedPathUnlockJpaRepository extends JpaRepository<AdvancedPathUnlock, Integer> {

    List<AdvancedPathUnlock> findByStudentIdOrderByIdAsc(int studentId);

    boolean existsByRedemptionId(int redemptionId);
}
