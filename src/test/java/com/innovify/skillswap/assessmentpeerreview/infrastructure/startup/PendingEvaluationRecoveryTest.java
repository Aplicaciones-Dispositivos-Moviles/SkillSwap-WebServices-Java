package com.innovify.skillswap.assessmentpeerreview.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class PendingEvaluationRecoveryTest {

    private final FakeMiniProjectSubmissionRepository submissions = new FakeMiniProjectSubmissionRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final PendingEvaluationRecovery recovery = new PendingEvaluationRecovery(submissions, events);

    private MiniProjectSubmission delivered(int miniProjectId, String submittedAt) {
        MiniProjectSubmission submission = new MiniProjectSubmission(miniProjectId, 7, 10 + miniProjectId,
                "http-basics", "https://github.com/ana/repo-" + miniProjectId);
        ReflectionTestUtils.setField(submission, "submittedAt", Instant.parse(submittedAt));
        return submissions.save(submission);
    }

    @Test
    void recover_announcesTheDeliveriesWithoutEvaluationOldestFirst() {
        MiniProjectSubmission newest = delivered(1, "2026-10-22T12:00:00Z");
        MiniProjectSubmission oldest = delivered(2, "2026-10-22T08:00:00Z");
        MiniProjectSubmission middle = delivered(3, "2026-10-22T10:00:00Z");

        int announced = recovery.recover();

        assertThat(announced).isEqualTo(3);
        assertThat(events.published()).extracting(event -> ((MiniProjectSubmitted) event).submissionId())
                .containsExactly(oldest.getId(), middle.getId(), newest.getId());
    }

    @Test
    void recover_announcesEveryFieldOfTheDelivery() {
        MiniProjectSubmission submission = delivered(4, "2026-10-22T08:00:00Z");

        recovery.recover();

        assertThat(events.published()).containsExactly(new MiniProjectSubmitted(submission.getId(), 4, 7, 14,
                "http-basics", "https://github.com/ana/repo-4", Instant.parse("2026-10-22T08:00:00Z")));
    }

    @Test
    void recover_skipsTheDeliveriesThatWereAlreadyEvaluated() {
        MiniProjectSubmission approved = delivered(1, "2026-10-22T08:00:00Z");
        approved.approve(FakeMiniProjectEvaluationService.ENOUGH, 70);
        MiniProjectSubmission pending = delivered(2, "2026-10-22T09:00:00Z");

        assertThat(recovery.recover()).isEqualTo(1);
        assertThat(events.published()).extracting(event -> ((MiniProjectSubmitted) event).submissionId())
                .containsExactly(pending.getId());
    }

    @Test
    void recover_withNothingPending_announcesNothing() {
        assertThat(recovery.recover()).isZero();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void recover_whenOneAnnouncementFails_theOthersGoOn() {
        delivered(1, "2026-10-22T08:00:00Z");
        MiniProjectSubmission second = delivered(2, "2026-10-22T09:00:00Z");
        List<DomainEvent> received = new ArrayList<>();
        DomainEventPublisher flaky = new DomainEventPublisher() {
            @Override
            public <E extends DomainEvent> void publish(E event) {
                if (received.isEmpty() && ((MiniProjectSubmitted) event).miniProjectId() == 1) {
                    received.add(event);
                    throw new IllegalStateException("boom");
                }
                received.add(event);
            }
        };

        int announced = new PendingEvaluationRecovery(submissions, flaky).recover();

        assertThat(announced).isEqualTo(1);
        assertThat(received).hasSize(2);
        assertThat(((MiniProjectSubmitted) received.get(1)).submissionId()).isEqualTo(second.getId());
    }

    @Test
    void recover_whenTheDatabaseFails_doesNotThrow() {
        FakeMiniProjectSubmissionRepository broken = new FakeMiniProjectSubmissionRepository() {
            @Override
            public List<MiniProjectSubmission> findAwaitingEvaluation() {
                throw new DataAccessResourceFailureException("down");
            }
        };

        assertThat(new PendingEvaluationRecovery(broken, events).recover()).isZero();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void whenTheApplicationIsReady_itRecovers() {
        delivered(1, "2026-10-22T08:00:00Z");

        recovery.onApplicationEvent((ApplicationReadyEvent) null);

        assertThat(events.published()).hasSize(1);
    }
}
