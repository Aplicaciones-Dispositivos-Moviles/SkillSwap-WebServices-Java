package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * How long a verifier has to review a case the student escalates: a number of hours, or of business days (Monday
 * to Friday).
 *
 * @param amount how many units
 * @param unit   hours or business days
 */
public record ReviewDeadline(int amount, Unit unit) {

    public enum Unit {
        HOURS("Hours"),
        BUSINESS_DAYS("BusinessDays");

        private final String value;

        Unit(String value) {
            this.value = value;
        }

        /** The representation exposed by the API. */
        public String value() {
            return value;
        }
    }

    public ReviewDeadline {
        if (amount <= 0) {
            throw new DomainException("The review deadline must be positive.");
        }
        if (unit == null) {
            throw new DomainException("The unit of the review deadline is required.");
        }
    }

    public static ReviewDeadline hours(int amount) {
        return new ReviewDeadline(amount, Unit.HOURS);
    }

    public static ReviewDeadline businessDays(int amount) {
        return new ReviewDeadline(amount, Unit.BUSINESS_DAYS);
    }
}
