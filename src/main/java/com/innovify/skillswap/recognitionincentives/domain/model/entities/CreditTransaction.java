package com.innovify.skillswap.recognitionincentives.domain.model.entities;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;

/**
 * A movement of a wallet: credits earned by resolving a case or credits redeemed for a benefit. A transaction
 * never changes once it was recorded. An earned transaction can point to the case that originated it, which
 * keeps a case from crediting twice.
 */
public class CreditTransaction {

    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private Integer id;
    private int walletId;
    private Credits amount;
    private TransactionType type;
    private String description;
    private Integer relatedCaseId;
    private Instant createdAt;

    /** Required by JPA once the entity is mapped. */
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
