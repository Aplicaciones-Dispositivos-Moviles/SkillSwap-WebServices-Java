package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * How long a verifier has to review a case, set by the plan of the student when the case is opened: a number of
 * hours, or of business days (Monday to Friday, in Peru time; public holidays are not considered).
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

        /** The representation stored in the database and exposed by the API. */
        public String value() {
            return value;
        }

        /** Case-insensitive lookup by {@link #value()}. */
        public static Unit fromValue(String value) {
            for (Unit candidate : values()) {
                if (candidate.value.equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Unknown ReviewDeadline.Unit: " + value);
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

    /**
     * When the review of a case opened at that moment is due. Business days keep the time of the day: a case opened
     * on Friday at 10:00 with 5 business days is due the next Friday at 10:00, and one opened on Saturday is due on
     * Friday too.
     */
    public Instant dueFrom(Instant openedAt) {
        if (openedAt == null) {
            throw new DomainException("The opening moment is required.");
        }
        if (unit == Unit.HOURS) {
            return openedAt.plus(amount, ChronoUnit.HOURS);
        }

        ZonedDateTime due = openedAt.atZone(EscalationCalendar.ZONE);
        int added = 0;
        while (added < amount) {
            due = due.plusDays(1);
            if (due.getDayOfWeek() != DayOfWeek.SATURDAY && due.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return due.toInstant();
    }
}
