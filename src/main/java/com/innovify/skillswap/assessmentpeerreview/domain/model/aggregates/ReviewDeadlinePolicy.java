package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * The time a verifier has to resolve the cases of the students of one plan, defined by a Verificador senior (US39).
 * It applies to the cases opened from then on; the cases already open keep the deadline they were opened with.
 *
 * <p>Each plan promises a maximum, so the deadline can be shortened but never made longer than the plan offers: up
 * to 48 hours on the monthly plan (Premium) and up to 5 business days on the free plan (Free).
 */
@Entity
@Table(name = "review_deadline_policies")
public class ReviewDeadlinePolicy {

    public static final String PREMIUM_PLAN = "Premium";
    public static final String FREE_PLAN = "Free";
    public static final int MAX_PREMIUM_HOURS = 48;
    public static final int MAX_FREE_BUSINESS_DAYS = 5;

    /** Free or Premium, as Subscription &amp; Billing names the plans. */
    @Id
    @Column(name = "plan", nullable = false, length = 20)
    private String plan;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "updated_by_user_id", nullable = false)
    private int updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected ReviewDeadlinePolicy() {
    }

    /** @throws DomainException when the plan or the deadline are not valid, or the senior is not a valid user */
    public ReviewDeadlinePolicy(String plan, ReviewDeadline deadline, int definedByUserId) {
        if (!PREMIUM_PLAN.equals(plan) && !FREE_PLAN.equals(plan)) {
            throw new DomainException("The plan must be Free or Premium.");
        }
        this.plan = plan;
        define(deadline, definedByUserId);
    }

    /**
     * Changes the deadline of the plan.
     *
     * @throws DomainException when the deadline is not valid for the plan or the senior is not a valid user
     */
    public ReviewDeadlinePolicy define(ReviewDeadline deadline, int definedByUserId) {
        if (!isValidFor(plan, deadline)) {
            throw new DomainException(PREMIUM_PLAN.equals(plan)
                    ? "The monthly plan allows between 1 and %d hours.".formatted(MAX_PREMIUM_HOURS)
                    : "The free plan allows between 1 and %d business days.".formatted(MAX_FREE_BUSINESS_DAYS));
        }
        if (definedByUserId <= 0) {
            throw new DomainException("The deadline must be defined by a valid user.");
        }
        this.amount = deadline.amount();
        this.unit = deadline.unit().value();
        this.updatedByUserId = definedByUserId;
        this.updatedAt = Instant.now();
        return this;
    }

    /** Whether the deadline fits what the plan promises: hours for Premium, business days for Free. */
    public static boolean isValidFor(String plan, ReviewDeadline deadline) {
        if (deadline == null) {
            return false;
        }
        if (PREMIUM_PLAN.equals(plan)) {
            return deadline.unit() == ReviewDeadline.Unit.HOURS && deadline.amount() <= MAX_PREMIUM_HOURS;
        }
        if (FREE_PLAN.equals(plan)) {
            return deadline.unit() == ReviewDeadline.Unit.BUSINESS_DAYS && deadline.amount() <= MAX_FREE_BUSINESS_DAYS;
        }
        return false;
    }

    /** The deadline the plan has while no senior defined another: 48 hours (Premium) or 5 business days (Free). */
    public static ReviewDeadline defaultFor(String plan) {
        return PREMIUM_PLAN.equals(plan)
                ? ReviewDeadline.hours(MAX_PREMIUM_HOURS)
                : ReviewDeadline.businessDays(MAX_FREE_BUSINESS_DAYS);
    }

    public String getPlan() {
        return plan;
    }

    public ReviewDeadline getDeadline() {
        return new ReviewDeadline(amount, ReviewDeadline.Unit.fromValue(unit));
    }

    public int getUpdatedByUserId() {
        return updatedByUserId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReviewDeadlinePolicy policy && Objects.equals(plan, policy.plan);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(plan);
    }
}
