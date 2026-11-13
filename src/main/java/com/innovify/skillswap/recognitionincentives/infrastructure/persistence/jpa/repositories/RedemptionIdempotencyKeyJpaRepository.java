package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the "redemption_idempotency_keys" table. Only
 * {@link RedemptionIdempotencyKeyRepositoryAdapter} uses it.
 */
public interface RedemptionIdempotencyKeyJpaRepository extends JpaRepository<RedemptionIdempotencyKey, Integer> {

    Optional<RedemptionIdempotencyKey> findByUserIdAndKey(int userId, String key);
}
