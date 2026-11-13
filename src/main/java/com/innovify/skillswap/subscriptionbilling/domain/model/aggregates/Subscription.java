package com.innovify.skillswap.subscriptionbilling.domain.model.aggregates;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.Money;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The monthly subscription of a student, which lifts the limits of the free plan. It is created only after the
 * payment gateway confirmed the purchase, and its period follows what the gateway reports. Cancelling keeps the
 * plan until the end of the period already paid; once it expires the student is back on the free plan, and a new
 * purchase starts a new subscription (the expired ones stay as history).
 *
 * <p>The plan is stored flattened in the plan_* columns.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    public static final int MAX_STORE_TRANSACTION_ID_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "plan_name", nullable = false, length = SubscriptionPlan.MAX_NAME_LENGTH)
    private String planName;

    @Column(name = "plan_product_id", nullable = false, length = SubscriptionPlan.MAX_PRODUCT_ID_LENGTH)
    private String planProductId;

    @Column(name = "plan_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal planPrice;

    @Column(name = "plan_currency", nullable = false, length = 3)
    private String planCurrency;

    /** Stored as "Active", "Cancelled" or "Expired". */
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "store_transaction_id", length = MAX_STORE_TRANSACTION_ID_LENGTH)
    private String storeTransactionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Subscription() {
    }

    /**
     * Activates the subscription of a purchase the payment gateway already verified.
     *
     * @param storeTransactionId the store transaction reported by the gateway, or null when it reports none
     * @param currentPeriodEnd   when the period already paid ends
     * @throws DomainException when the student, plan, transaction or period are not valid
     */
    public Subscription(int studentId, SubscriptionPlan plan, String storeTransactionId, Instant currentPeriodEnd) {
        if (studentId <= 0) {
            throw new DomainException("The subscription must belong to a valid student.");
        }
        if (plan == null) {
            throw new DomainException("The subscription needs a plan.");
        }
        Instant now = Instant.now();
        if (currentPeriodEnd == null || !currentPeriodEnd.isAfter(now)) {
            throw new DomainException("The paid period must end in the future.");
        }

        this.studentId = studentId;
        this.planName = plan.name();
        this.planProductId = plan.productId();
        this.planPrice = plan.price().amount();
        this.planCurrency = plan.price().currency();
        this.storeTransactionId = normalizeTransaction(storeTransactionId);
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = now;
        this.currentPeriodEnd = currentPeriodEnd;
        this.updatedAt = now;
    }

    public Integer getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public SubscriptionPlan getPlan() {
        return new SubscriptionPlan(planName, planProductId, new Money(planPrice, planCurrency));
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    /** The Google Play transaction reported by the gateway, to reconcile the subscription with the purchase. */
    public String getStoreTransactionId() {
        return storeTransactionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** When the period already paid ends: the next renewal, or the end of the plan once cancelled. */
    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isOwnedBy(int studentId) {
        return this.studentId == studentId;
    }

    public boolean isExpired() {
        return status == SubscriptionStatus.EXPIRED;
    }

    public boolean isCancelled() {
        return status == SubscriptionStatus.CANCELLED;
    }

    /**
     * Whether it grants the paid plan at that moment: it is not expired and the paid period has not ended. A
     * period that ended without news from the gateway no longer counts, even before it is marked expired.
     */
    public boolean grantsPremiumAt(Instant moment) {
        return status != SubscriptionStatus.EXPIRED && currentPeriodEnd.isAfter(moment);
    }

    /**
     * Extends the paid period after the store renewed it. A renewal never shortens the period, so a repeated or
     * late notification changes nothing.
     *
     * @return whether the period was extended
     * @throws DomainException when the subscription expired or the new end is missing
     */
    public boolean renew(Instant newPeriodEnd, String storeTransactionId) {
        requireNotExpired("renewed");
        if (newPeriodEnd == null) {
            throw new DomainException("The renewed period needs an end.");
        }
        if (!newPeriodEnd.isAfter(currentPeriodEnd)) {
            return false;
        }

        this.currentPeriodEnd = newPeriodEnd;
        String transaction = normalizeTransaction(storeTransactionId);
        if (transaction != null) {
            this.storeTransactionId = transaction;
        }
        this.updatedAt = Instant.now();
        return true;
    }

    /**
     * Stops the renewals. The plan is kept until the end of the period already paid.
     *
     * @throws DomainException when the subscription is not active
     */
    public Subscription cancel() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new DomainException("Only an active subscription can be cancelled.");
        }

        Instant now = Instant.now();
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
        return this;
    }

    /**
     * Undoes a cancellation made before the period ended: the store will renew it again.
     *
     * @throws DomainException when the subscription is not cancelled
     */
    public Subscription uncancel() {
        if (status != SubscriptionStatus.CANCELLED) {
            throw new DomainException("Only a cancelled subscription can be resumed.");
        }

        this.status = SubscriptionStatus.ACTIVE;
        this.cancelledAt = null;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Ends the subscription: the store did not renew it (or revoked it) and the student is back on the free plan.
     *
     * @throws DomainException when it is already expired
     */
    public Subscription expire() {
        requireNotExpired("expired again");

        Instant now = Instant.now();
        this.status = SubscriptionStatus.EXPIRED;
        this.expiredAt = now;
        this.updatedAt = now;
        return this;
    }

    private void requireNotExpired(String action) {
        if (status == SubscriptionStatus.EXPIRED) {
            throw new DomainException("An expired subscription cannot be " + action + ".");
        }
    }

    private static String normalizeTransaction(String storeTransactionId) {
        if (storeTransactionId == null || storeTransactionId.isBlank()) {
            return null;
        }
        String transaction = storeTransactionId.strip();
        if (transaction.length() > MAX_STORE_TRANSACTION_ID_LENGTH) {
            throw new DomainException("The store transaction id cannot exceed %d characters."
                    .formatted(MAX_STORE_TRANSACTION_ID_LENGTH));
        }
        return transaction;
    }
}
