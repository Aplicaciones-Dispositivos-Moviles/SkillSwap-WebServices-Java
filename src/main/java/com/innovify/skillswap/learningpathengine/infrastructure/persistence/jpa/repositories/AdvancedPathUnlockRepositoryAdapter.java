package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import com.innovify.skillswap.learningpathengine.domain.repositories.AdvancedPathUnlockRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link AdvancedPathUnlockRepository} port on top of Spring Data JPA. */
@Repository
public class AdvancedPathUnlockRepositoryAdapter implements AdvancedPathUnlockRepository {

    private final AdvancedPathUnlockJpaRepository jpaRepository;

    public AdvancedPathUnlockRepositoryAdapter(AdvancedPathUnlockJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AdvancedPathUnlock save(AdvancedPathUnlock unlock) {
        return jpaRepository.saveAndFlush(unlock);
    }

    @Override
    public List<AdvancedPathUnlock> findByStudentId(int studentId) {
        return jpaRepository.findByStudentIdOrderByIdAsc(studentId);
    }

    @Override
    public boolean existsByRedemptionId(int redemptionId) {
        return jpaRepository.existsByRedemptionId(redemptionId);
    }
}
