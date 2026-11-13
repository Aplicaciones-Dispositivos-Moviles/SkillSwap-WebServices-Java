package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link StudentEmployabilityScoreRepository} port on top of Spring Data JPA. */
@Repository
public class StudentEmployabilityScoreRepositoryAdapter implements StudentEmployabilityScoreRepository {

    private final StudentEmployabilityScoreJpaRepository jpaRepository;

    public StudentEmployabilityScoreRepositoryAdapter(StudentEmployabilityScoreJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StudentEmployabilityScore save(StudentEmployabilityScore score) {
        return jpaRepository.saveAndFlush(score);
    }

    @Override
    public Optional<StudentEmployabilityScore> findByStudentId(int studentId) {
        return jpaRepository.findByStudentId(studentId);
    }
}
