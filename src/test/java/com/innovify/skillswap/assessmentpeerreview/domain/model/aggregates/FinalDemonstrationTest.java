package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinalDemonstrationTest {

    private static final Instant IN_TWO_DAYS = Instant.now().plus(Duration.ofDays(2));

    private static FinalDemonstration scheduled() {
        return new FinalDemonstration(1, 5, " java ", 9, DemonstrationModality.ADVANCED_PROJECT, IN_TWO_DAYS);
    }

    @Test
    void newDemonstration_isScheduledWithItsData() {
        FinalDemonstration demonstration = scheduled();

        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
        assertThat(demonstration.getStudentId()).isEqualTo(1);
        assertThat(demonstration.getLearningPathId()).isEqualTo(5);
        assertThat(demonstration.getSkillTag()).isEqualTo("java");
        assertThat(demonstration.getVerifierUserId()).isEqualTo(9);
        assertThat(demonstration.getModality()).isEqualTo(DemonstrationModality.ADVANCED_PROJECT);
        assertThat(demonstration.getCreatedAt()).isNotNull();
        assertThat(demonstration.isOwnedBy(1)).isTrue();
        assertThat(demonstration.isOwnedBy(9)).isFalse();
        assertThat(demonstration.isAssignedTo(9)).isTrue();
    }

    @Test
    void newDemonstration_withInvalidData_isRejected() {
        assertThatThrownBy(() -> new FinalDemonstration(0, 5, "java", 9, DemonstrationModality.SUPERVISED_EXAM,
                IN_TWO_DAYS)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FinalDemonstration(1, 0, "java", 9, DemonstrationModality.SUPERVISED_EXAM,
                IN_TWO_DAYS)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FinalDemonstration(1, 5, " ", 9, DemonstrationModality.SUPERVISED_EXAM,
                IN_TWO_DAYS)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FinalDemonstration(1, 5, "java", 0, DemonstrationModality.SUPERVISED_EXAM,
                IN_TWO_DAYS)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FinalDemonstration(1, 5, "java", 9, null, IN_TWO_DAYS))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FinalDemonstration(1, 5, "java", 9, DemonstrationModality.SUPERVISED_EXAM,
                null)).isInstanceOf(DomainException.class);
    }

    @Test
    void newDemonstration_theStudentCannotBeItsVerifier() {
        assertThatThrownBy(() -> new FinalDemonstration(4, 5, "java", 4, DemonstrationModality.SUPERVISED_EXAM,
                IN_TWO_DAYS)).isInstanceOf(DomainException.class);
    }

    @Test
    void isFarEnough_requiresAtLeast24HoursAhead() {
        Instant now = Instant.now();

        assertThat(FinalDemonstration.isFarEnough(now.plus(Duration.ofHours(25)), now)).isTrue();
        assertThat(FinalDemonstration.isFarEnough(now.plus(Duration.ofHours(24)), now)).isTrue();
        assertThat(FinalDemonstration.isFarEnough(now.plus(Duration.ofHours(23)), now)).isFalse();
        assertThat(FinalDemonstration.isFarEnough(now.minus(Duration.ofHours(1)), now)).isFalse();
        assertThat(FinalDemonstration.isFarEnough(null, now)).isFalse();
    }

    @Test
    void reschedule_movesAScheduledDemonstration() {
        FinalDemonstration demonstration = scheduled();
        Instant later = IN_TWO_DAYS.plus(Duration.ofDays(1));

        demonstration.reschedule(later);

        assertThat(demonstration.getScheduledAt()).isEqualTo(later.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
    }

    @Test
    void reschedule_withoutAMoment_isRejected() {
        assertThatThrownBy(() -> scheduled().reschedule(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_cancelsAScheduledDemonstrationOnce() {
        FinalDemonstration demonstration = scheduled();

        demonstration.cancel();

        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.CANCELLED);
        assertThat(demonstration.isScheduled()).isFalse();
        assertThatThrownBy(demonstration::cancel).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> demonstration.reschedule(IN_TWO_DAYS)).isInstanceOf(DomainException.class);
    }

    @Test
    void status_blocksANewDemonstrationOnlyWhileItCounts() {
        assertThat(DemonstrationStatus.SCHEDULED.blocksNewDemonstration()).isTrue();
        assertThat(DemonstrationStatus.VIDEO_SUBMITTED.blocksNewDemonstration()).isTrue();
        assertThat(DemonstrationStatus.APPROVED.blocksNewDemonstration()).isTrue();
        assertThat(DemonstrationStatus.REJECTED.blocksNewDemonstration()).isFalse();
        assertThat(DemonstrationStatus.CANCELLED.blocksNewDemonstration()).isFalse();
    }

    @Test
    void valueObjects_areFoundByTheirStoredValue() {
        assertThat(DemonstrationModality.fromValue("supervisedexam")).isEqualTo(DemonstrationModality.SUPERVISED_EXAM);
        assertThat(DemonstrationStatus.fromValue("VideoSubmitted")).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
        assertThatThrownBy(() -> DemonstrationModality.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DemonstrationStatus.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Video and review (US29) ----------

    private static FinalDemonstration withVideo() {
        return scheduled().attachVideo("demonstrations/1/abc.mp4", "video/mp4", 1024);
    }

    @Test
    void attachVideo_marksTheVideoSubmitted() {
        FinalDemonstration demonstration = scheduled();

        demonstration.attachVideo(" demonstrations/1/abc.mp4 ", "video/mp4", 2048);

        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
        assertThat(demonstration.hasVideo()).isTrue();
        assertThat(demonstration.getVideoStorageReference()).isEqualTo("demonstrations/1/abc.mp4");
        assertThat(demonstration.getVideoContentType()).isEqualTo("video/mp4");
        assertThat(demonstration.getVideoSizeBytes()).isEqualTo(2048L);
        assertThat(demonstration.getVideoSubmittedAt()).isNotNull();
    }

    @Test
    void attachVideo_replacesTheVideoUntilItIsReviewed() {
        FinalDemonstration demonstration = withVideo();

        demonstration.attachVideo("demonstrations/1/new.webm", "video/webm", 4096);

        assertThat(demonstration.getVideoStorageReference()).isEqualTo("demonstrations/1/new.webm");
        assertThat(demonstration.getVideoContentType()).isEqualTo("video/webm");

        demonstration.review(90, null);

        assertThat(demonstration.acceptsVideo()).isFalse();
        assertThatThrownBy(() -> demonstration.attachVideo("x.mp4", "video/mp4", 1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void attachVideo_ofACancelledDemonstration_orWithInvalidData_isRejected() {
        FinalDemonstration cancelled = scheduled().cancel();

        assertThatThrownBy(() -> cancelled.attachVideo("x.mp4", "video/mp4", 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> scheduled().attachVideo(" ", "video/mp4", 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> scheduled().attachVideo("x.mp4", null, 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> scheduled().attachVideo("x.mp4", "video/mp4", 0)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_afterTheVideoWasSubmitted_isRejected() {
        assertThatThrownBy(() -> withVideo().cancel()).isInstanceOf(DomainException.class);
    }

    @Test
    void review_withThePassingScoreApproves() {
        FinalDemonstration demonstration = withVideo();

        demonstration.review(70, "  Good work  ");

        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.APPROVED);
        assertThat(demonstration.getScore()).isEqualTo(70);
        assertThat(demonstration.getFeedback()).isEqualTo("Good work");
        assertThat(demonstration.getReviewedAt()).isNotNull();
        assertThat(demonstration.isReviewed()).isTrue();
    }

    @Test
    void review_underThePassingScoreRejectsAndNeedsAFeedback() {
        FinalDemonstration demonstration = withVideo();

        assertThatThrownBy(() -> demonstration.review(69, " ")).isInstanceOf(DomainException.class);

        demonstration.review(69, "The tests are missing.");

        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.REJECTED);
        assertThat(demonstration.getFeedback()).isEqualTo("The tests are missing.");
    }

    @Test
    void review_withInvalidDataOrWithoutVideo_isRejected() {
        FinalDemonstration demonstration = withVideo();

        assertThatThrownBy(() -> demonstration.review(-1, "x")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> demonstration.review(101, "x")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> demonstration.review(80, "x".repeat(1001))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> scheduled().review(90, null)).isInstanceOf(DomainException.class);
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
    }

    @Test
    void review_twice_isRejected() {
        FinalDemonstration demonstration = withVideo();
        demonstration.review(95, null);

        assertThatThrownBy(() -> demonstration.review(10, "No")).isInstanceOf(DomainException.class);
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.APPROVED);
    }
}
