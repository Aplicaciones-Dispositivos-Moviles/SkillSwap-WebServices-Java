package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeFinalDemonstrationRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.DemonstrationAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CancelFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.RescheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ScheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationCancelled;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationScheduled;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** The path 5 of student 1 is completed and has the skills "java" and "sql"; verifier 9 is enabled in "java". */
class FinalDemonstrationCommandServiceImplTest {

    private static final int STUDENT = 1;
    private static final int PATH = 5;
    private static final int VERIFIER = 9;

    private final FakeFinalDemonstrationRepository demonstrations = new FakeFinalDemonstrationRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final AtomicInteger transactions = new AtomicInteger();
    private FinalDemonstrationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };
        service = new FinalDemonstrationCommandServiceImpl(demonstrations, learningPath,
                new DemonstrationAssignmentServiceImpl(profiles, cases, demonstrations, new DefaultVerifierMatcher()),
                events, counting, TestMessages.source());
        learningPath.addPath(PATH, STUDENT, true, "java", "sql");
        profiles.save(new VerifierProfile(VERIFIER, "java"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    private Result<FinalDemonstration> schedule() {
        return service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, PATH,
                DemonstrationModality.ADVANCED_PROJECT, inDays(3)));
    }

    private void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- Schedule (US28) ----------

    @Test
    void schedule_ofACompletedPath_assignsAnEnabledVerifierAndPublishesTheEvent() {
        Result<FinalDemonstration> result = schedule();

        assertThat(result.isSuccess()).isTrue();
        FinalDemonstration demonstration = result.value();
        assertThat(demonstration.getId()).isNotNull();
        assertThat(demonstration.getStudentId()).isEqualTo(STUDENT);
        assertThat(demonstration.getLearningPathId()).isEqualTo(PATH);
        assertThat(demonstration.getVerifierUserId()).isEqualTo(VERIFIER);
        assertThat(demonstration.getSkillTag()).isEqualTo("java");
        assertThat(demonstration.getModality()).isEqualTo(DemonstrationModality.ADVANCED_PROJECT);
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
        assertThat(events.published()).containsExactly(new FinalDemonstrationScheduled(demonstration.getId(),
                STUDENT, VERIFIER, "java", demonstration.getScheduledAt()));
        assertThat(transactions.get()).isEqualTo(1);
    }

    @Test
    void schedule_ofAPathThatIsNotCompleted_isRejected() {
        learningPath.addPath(6, STUDENT, false, "java");

        Result<FinalDemonstration> result = service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, 6,
                DemonstrationModality.SUPERVISED_EXAM, inDays(3)));

        assertFailure(result, AssessmentPeerReviewError.PATH_NOT_COMPLETED);
        assertThat(demonstrations.demonstrations()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void schedule_ofAnUnknownPath_isRejected() {
        assertFailure(service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, 99,
                DemonstrationModality.SUPERVISED_EXAM, inDays(3))), AssessmentPeerReviewError.LEARNING_PATH_NOT_FOUND);
    }

    @Test
    void schedule_ofAnotherStudentsPath_isRejected() {
        assertFailure(service.handle(new ScheduleFinalDemonstrationCommand(2, PATH,
                DemonstrationModality.SUPERVISED_EXAM, inDays(3))), AssessmentPeerReviewError.NOT_PATH_OWNER);
    }

    @Test
    void schedule_withoutModalityOrMoment_isRejected() {
        assertFailure(service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, PATH, null, inDays(3))),
                AssessmentPeerReviewError.INVALID_MODALITY);
        assertFailure(service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, PATH,
                DemonstrationModality.SUPERVISED_EXAM, null)), AssessmentPeerReviewError.INVALID_SCHEDULE);
    }

    @Test
    void schedule_lessThan24HoursAhead_isRejected() {
        Result<FinalDemonstration> result = service.handle(new ScheduleFinalDemonstrationCommand(STUDENT, PATH,
                DemonstrationModality.SUPERVISED_EXAM, Instant.now().plus(Duration.ofHours(2))));

        assertFailure(result, AssessmentPeerReviewError.SCHEDULE_TOO_SOON);
        assertThat(demonstrations.demonstrations()).isEmpty();
    }

    @Test
    void schedule_withoutAnAvailableVerifier_isRejectedAndSavesNothing() {
        profiles.profiles().get(0).setAvailability(false);

        assertFailure(schedule(), AssessmentPeerReviewError.NO_VERIFIER_AVAILABLE);
        assertThat(demonstrations.demonstrations()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void schedule_whenThePathAlreadyHasOne_isRejected() {
        schedule();

        assertFailure(schedule(), AssessmentPeerReviewError.DEMONSTRATION_ALREADY_ACTIVE);
        assertThat(demonstrations.demonstrations()).hasSize(1);
    }

    @Test
    void schedule_afterACancellation_isAllowed() {
        FinalDemonstration first = schedule().value();
        service.handle(new CancelFinalDemonstrationCommand(first.getId(), STUDENT));

        assertThat(schedule().isSuccess()).isTrue();
        assertThat(demonstrations.demonstrations()).hasSize(2);
    }

    @Test
    void schedule_whenTwoRequestsRace_theLoserHearsItIsAlreadyActive() {
        // The other request saves its demonstration after the check of this one, so the unique index rejects the save.
        FakeFinalDemonstrationRepository racing = new FakeFinalDemonstrationRepository() {
            @Override
            public FinalDemonstration save(FinalDemonstration demonstration) {
                super.save(new FinalDemonstration(STUDENT, PATH, "java", VERIFIER,
                        DemonstrationModality.SUPERVISED_EXAM, inDays(5)));
                throw new DataIntegrityViolationException("uq_final_demonstrations_active_path");
            }
        };
        FinalDemonstrationCommandServiceImpl racingService = new FinalDemonstrationCommandServiceImpl(racing,
                learningPath, new DemonstrationAssignmentServiceImpl(profiles, cases, racing,
                new DefaultVerifierMatcher()), events, TransactionOperations.withoutTransaction(),
                TestMessages.source());

        Result<FinalDemonstration> result = racingService.handle(new ScheduleFinalDemonstrationCommand(STUDENT, PATH,
                DemonstrationModality.ADVANCED_PROJECT, inDays(3)));

        assertFailure(result, AssessmentPeerReviewError.DEMONSTRATION_ALREADY_ACTIVE);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void schedule_whenTheDatabaseFails_returnsADatabaseError() {
        demonstrations.failOnSave(new org.springframework.dao.DataAccessResourceFailureException("down"));

        assertFailure(schedule(), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void schedule_picksTheVerifierWithTheFewestActiveDemonstrations() {
        profiles.save(new VerifierProfile(10, "sql"));
        demonstrations.save(new FinalDemonstration(2, 20, "java", VERIFIER, DemonstrationModality.SUPERVISED_EXAM,
                inDays(4)));

        FinalDemonstration chosen = schedule().value();

        assertThat(chosen.getVerifierUserId()).isEqualTo(10);
        assertThat(chosen.getSkillTag()).isEqualTo("sql");
    }

    // ---------- Reschedule ----------

    @Test
    void reschedule_movesTheDemonstrationOfTheStudent() {
        FinalDemonstration demonstration = schedule().value();
        Instant later = inDays(6);

        Result<FinalDemonstration> result = service.handle(
                new RescheduleFinalDemonstrationCommand(demonstration.getId(), STUDENT, later));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getScheduledAt()).isEqualTo(later.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(demonstrations.lockedDemonstrations()).containsExactly(demonstration.getId());
    }

    @Test
    void reschedule_withAMomentTooSoonOrMissing_isRejected() {
        FinalDemonstration demonstration = schedule().value();

        assertFailure(service.handle(new RescheduleFinalDemonstrationCommand(demonstration.getId(), STUDENT,
                Instant.now().plus(Duration.ofHours(1)))), AssessmentPeerReviewError.SCHEDULE_TOO_SOON);
        assertFailure(service.handle(new RescheduleFinalDemonstrationCommand(demonstration.getId(), STUDENT, null)),
                AssessmentPeerReviewError.INVALID_SCHEDULE);
    }

    @Test
    void reschedule_ofAnotherStudentsDemonstration_isRejected() {
        FinalDemonstration demonstration = schedule().value();

        assertFailure(service.handle(new RescheduleFinalDemonstrationCommand(demonstration.getId(), 2, inDays(6))),
                AssessmentPeerReviewError.NOT_DEMONSTRATION_OWNER);
    }

    @Test
    void reschedule_ofAnUnknownOrCancelledDemonstration_isRejected() {
        assertFailure(service.handle(new RescheduleFinalDemonstrationCommand(99, STUDENT, inDays(6))),
                AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);

        FinalDemonstration demonstration = schedule().value();
        service.handle(new CancelFinalDemonstrationCommand(demonstration.getId(), STUDENT));

        assertFailure(service.handle(new RescheduleFinalDemonstrationCommand(demonstration.getId(), STUDENT,
                inDays(6))), AssessmentPeerReviewError.DEMONSTRATION_NOT_SCHEDULED);
    }

    // ---------- Cancel ----------

    @Test
    void cancel_cancelsTheDemonstrationAndPublishesTheEvent() {
        FinalDemonstration demonstration = schedule().value();
        events.published().clear();

        Result<FinalDemonstration> result = service.handle(
                new CancelFinalDemonstrationCommand(demonstration.getId(), STUDENT));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(DemonstrationStatus.CANCELLED);
        assertThat(events.published()).containsExactly(
                new FinalDemonstrationCancelled(demonstration.getId(), STUDENT, VERIFIER));
    }

    @Test
    void cancel_twice_orOfAnotherStudent_isRejected() {
        FinalDemonstration demonstration = schedule().value();

        assertFailure(service.handle(new CancelFinalDemonstrationCommand(demonstration.getId(), 2)),
                AssessmentPeerReviewError.NOT_DEMONSTRATION_OWNER);

        service.handle(new CancelFinalDemonstrationCommand(demonstration.getId(), STUDENT));
        events.published().clear();

        assertFailure(service.handle(new CancelFinalDemonstrationCommand(demonstration.getId(), STUDENT)),
                AssessmentPeerReviewError.DEMONSTRATION_NOT_SCHEDULED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void cancel_ofAnUnknownDemonstration_isRejected() {
        assertFailure(service.handle(new CancelFinalDemonstrationCommand(99, STUDENT)),
                AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
    }
}
