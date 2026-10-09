package com.innovify.skillswap.recognitionincentives.domain.model.entities;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A movement of a wallet: credits earned by resolving a case or credits redeemed for a benefit. A transaction
 * never changes once it was recorded. An earned transaction can point to the case that originated it, which
 * keeps a case from crediting twice.
 */
@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

    public static final int MAX_DESCRIPTION_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "wallet_id", nullable = false)
    private int walletId;

    @Column(name = "amount", nullable = false)
    private Credits amount;

    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "description", nullable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "related_case_id")
    private Integer relatedCaseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected CreditTransaction() {
    }

    /**
     * @param relatedCaseId the case that paid an earned movement, or null
     * @throws DomainException when the wallet, amount, type, description or case are not valid
     */
    public CreditTransaction(int walletId, Credits amount, TransactionType type, String description,
                             Integer relatedCaseId) {
        if (walletId <= 0) {
            throw new DomainException("The transaction must belong to a valid wallet.");
        }
        if (amount == null || !amount.isPositive()) {
            throw new DomainException("The amount of a transaction must be positive.");
        }
        if (type == null) {
            throw new DomainException("The type of the transaction is not valid.");
        }

        String text = description == null ? "" : description.strip();
        if (text.isEmpty()) {
            throw new DomainException("The description of the transaction cannot be empty.");
        }
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainException(
                    "The description cannot exceed %d characters.".formatted(MAX_DESCRIPTION_LENGTH));
        }

        if (relatedCaseId != null) {
            if (type != TransactionType.EARNED) {
                throw new DomainException("Only an earned transaction can point to a verification case.");
            }
            if (relatedCaseId <= 0) {
                throw new DomainException("The related case must be valid.");
            }
        }

        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.description = text;
        this.relatedCaseId = relatedCaseId;
        this.createdAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getWalletId() {
        return walletId;
    }

    public Credits getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /** The verification case that originated an earned transaction, if any. */
    public Integer getRelatedCaseId() {
        return relatedCaseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
