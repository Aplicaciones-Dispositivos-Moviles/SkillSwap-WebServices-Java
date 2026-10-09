package com.innovify.skillswap.assessmentpeerreview.domain.services;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The calendar of the escalations to a verifier. The monthly quota of the plan counts the cases opened in the
 * current calendar month, and the months and business days are those of Peru, where the platform operates.
 */
public final class EscalationCalendar {

    public static final ZoneId ZONE = ZoneId.of("America/Lima");

    private EscalationCalendar() {
    }

    /** The first moment of the calendar month of that instant, in Peru time. */
    public static Instant startOfMonth(Instant moment) {
        ZonedDateTime local = moment.atZone(ZONE);
        return local.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
    }
}
