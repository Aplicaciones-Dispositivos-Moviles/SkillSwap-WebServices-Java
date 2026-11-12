package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.repositories.RedemptionIdempotencyKeyRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link RedemptionIdempotencyKeyRepository} port on top of Spring Data JPA. */
@Repository
public class RedemptionIdempotencyKeyRepositoryAdapter implements RedemptionIdempotencyKeyRepository {

    private final RedemptionIdempotencyKeyJpaRepository jpaRepository;

    public RedemptionIdempotencyKeyRepositoryAdapter(RedemptionIdempotencyKeyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RedemptionIdempotencyKey save(RedemptionIdempotencyKey key) {
        return jpaRepository.saveAndFlush(key);
    }

    @Override
    public Optional<RedemptionIdempotencyKey> findByUserIdAndKey(int userId, IdempotencyKey key) {
        return jpaRepository.findByUserIdAndKey(userId, key.value());
    }
}
