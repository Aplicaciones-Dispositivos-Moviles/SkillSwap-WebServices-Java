package com.innovify.skillswap.recognitionincentives.domain.model.aggregates;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * The SkillCredits balance of a user. There is no real money in it: the credits are earned by verifying and can
 * only be spent on benefits of the platform. The movements are recorded as
 * {@link com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction}.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "wallet_owner_id", nullable = false)
    private int walletOwnerId;

    @Column(name = "balance", nullable = false)
    private int balance;

    /** Required by JPA. */
    protected Wallet() {
    }

    /** @throws DomainException when the owner is not valid */
    public Wallet(int walletOwnerId) {
        if (walletOwnerId <= 0) {
            throw new DomainException("The wallet must belong to a valid user.");
        }

        this.walletOwnerId = walletOwnerId;
        this.balance = 0;
    }

    public Integer getId() {
        return id;
    }

    public int getWalletOwnerId() {
        return walletOwnerId;
    }

    public int getBalance() {
        return balance;
    }

    /** Whether the balance covers the amount. */
    public boolean canAfford(Credits amount) {
        Objects.requireNonNull(amount, "amount");
        return balance >= amount.value();
    }

    /** Adds earned credits to the balance.
     *
     * @throws DomainException when the amount is not positive or the balance would overflow
     */
    public Wallet credit(Credits amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new DomainException("The amount to credit must be positive.");
        }
        if ((long) balance + amount.value() > Integer.MAX_VALUE) {
            throw new DomainException("The balance cannot exceed the maximum supported.");
        }

        balance += amount.value();
        return this;
    }

    /** Takes redeemed credits from the balance.
     *
     * @throws DomainException when the amount is not positive or the balance does not cover it
     */
    public Wallet debit(Credits amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new DomainException("The amount to debit must be positive.");
        }
        if (!canAfford(amount)) {
            throw new DomainException("The balance is not enough for this amount.");
        }

        balance -= amount.value();
        return this;
    }
}
