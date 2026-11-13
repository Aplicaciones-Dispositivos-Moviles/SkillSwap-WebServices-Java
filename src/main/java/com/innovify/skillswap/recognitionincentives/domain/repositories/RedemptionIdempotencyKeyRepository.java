package com.innovify.skillswap.recognitionincentives.domain.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import java.util.Optional;

/** Persistence port of the {@link RedemptionIdempotencyKey} entity. */
public interface RedemptionIdempotencyKeyRepository {

    /**
     * Persists a new key and flushes right away, so a key the user already stored fails inside the current
     * transaction (with a {@link org.springframework.dao.DataIntegrityViolationException}).
     */
    RedemptionIdempotencyKey save(RedemptionIdempotencyKey key);

    /** The key the user stored with a successful redemption, if any. */
    Optional<RedemptionIdempotencyKey> findByUserIdAndKey(int userId, IdempotencyKey key);
}
