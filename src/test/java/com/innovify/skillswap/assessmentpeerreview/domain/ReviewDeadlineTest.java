package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReviewDeadlineTest {

    /** A moment in Peru time (UTC-5, no daylight saving). */
    private static Instant lima(String localDateTime) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(localDateTime), EscalationCalendar.ZONE).toInstant();
    }

    @Test
    void hours_addTheHoursWhateverTheDay() {
        Instant saturday = lima("2026-10-10T10:00");

        assertThat(ReviewDeadline.hours(48).dueFrom(saturday)).isEqualTo(saturday.plus(Duration.ofHours(48)));
    }

    @ParameterizedTest
    @CsvSource({
            // opened (Peru time)  -> due (Peru time), 5 business days
            "2026-10-12T09:30, 2026-10-19T09:30",   // Monday -> next Monday
            "2026-10-09T10:00, 2026-10-16T10:00",   // Friday -> next Friday
            "2026-10-10T10:00, 2026-10-16T10:00",   // Saturday -> Friday
            "2026-10-11T23:00, 2026-10-16T23:00",   // Sunday night -> Friday night
            "2026-10-14T18:00, 2026-10-21T18:00"    // Wednesday -> next Wednesday
    })
    void businessDays_skipTheWeekendsInPeruTime(String opened, String due) {
        assertThat(ReviewDeadline.businessDays(5).dueFrom(lima(opened))).isEqualTo(lima(due));
    }

    @Test
    void deadline_mustBePositiveWithAUnitAndAMoment() {
        assertThatThrownBy(() -> ReviewDeadline.hours(0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ReviewDeadline(5, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ReviewDeadline.hours(1).dueFrom(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void startOfMonth_isTheFirstMidnightOfTheMonthInPeruTime() {
        // 2026-11-01 03:00 UTC is still October 31 in Peru.
        Instant lateOctoberInPeru = Instant.parse("2026-11-01T03:00:00Z");

        assertThat(EscalationCalendar.startOfMonth(lateOctoberInPeru)).isEqualTo(lima("2026-10-01T00:00"));
        assertThat(EscalationCalendar.startOfMonth(lima("2026-11-01T00:00"))).isEqualTo(lima("2026-11-01T00:00"));
    }

    @Test
    void verificationCase_storesTheDeadlineOfThePlanWhenItIsOpened() {
        VerificationCase withDeadline = new VerificationCase(1, 2, 3, "http-basics", CaseType.QUIZ,
                ReviewDeadline.hours(48));
        VerificationCase legacy = new VerificationCase(1, 2, 3, "http-basics", CaseType.QUIZ);

        assertThat(withDeadline.getReviewDueAt()).isEqualTo(withDeadline.getOpenedAt().plus(Duration.ofHours(48)));
        assertThat(legacy.getReviewDueAt()).isNull();
    }
}
