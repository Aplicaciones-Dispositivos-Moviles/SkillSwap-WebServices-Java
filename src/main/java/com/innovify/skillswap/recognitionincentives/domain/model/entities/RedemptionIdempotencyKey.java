package com.innovify.skillswap.recognitionincentives.domain.model.entities;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The Idempotency-Key a user sent with a redemption, together with the movement that redemption recorded. It is
 * saved in the same transaction as the debit, so only a successful redemption has one, and a key belongs to one
 * user: the same key from another user is another redemption. It never changes once it was recorded.
 */
@Entity
@Table(name = "redemption_idempotency_keys")
public class RedemptionIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "idempotency_key", nullable = false, length = IdempotencyKey.MAX_LENGTH)
    private String key;

    @Column(name = "credit_transaction_id", nullable = false)
    private int creditTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected RedemptionIdempotencyKey() {
    }

    /**
     * @param creditTransactionId the movement the redemption recorded
     * @throws DomainException when the user, key or movement are not valid
     */
    public RedemptionIdempotencyKey(int userId, IdempotencyKey key, int creditTransactionId) {
        if (userId <= 0) {
            throw new DomainException("The idempotency key must belong to a valid user.");
        }
        if (key == null) {
            throw new DomainException("The idempotency key is required.");
        }
        if (creditTransactionId <= 0) {
            throw new DomainException("The idempotency key must point to a valid transaction.");
        }
        this.userId = userId;
        this.key = key.value();
        this.creditTransactionId = creditTransactionId;
        this.createdAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public IdempotencyKey getKey() {
        return new IdempotencyKey(key);
    }

    /** The movement the redemption recorded, answered again when the key is retried. */
    public int getCreditTransactionId() {
        return creditTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
