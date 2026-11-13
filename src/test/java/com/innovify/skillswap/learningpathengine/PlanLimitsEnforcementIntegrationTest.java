package com.innovify.skillswap.learningpathengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakePaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The limits of the plan on the learning paths against a real PostgreSQL: concurrent requests cannot go over the
 * limit, and an expired subscription pauses the paths over the limit of the free plan.
 */
class PlanLimitsEnforcementIntegrationTest extends PostgresIntegrationTest {

    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SQL = "quiero aprender SQL";
    private static final String HTTP = "quiero aprender HTTP";

    @Autowired
    private LearningPathCommandService commands;

    @Autowired
    private LearningPathRepository paths;

    @Autowired
    private SubscriptionCommandService subscriptions;

    @Autowired
    private PaymentGateway paymentGateway;

    private FakePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = (FakePaymentGateway) paymentGateway;
        gateway.reset();
    }

    private Result<LearningPath> declare(int studentId, String goal) {
        return commands.handle(new DeclareGoalCommand(studentId, goal));
    }

    private <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void subscribe(int studentId) {
        gateway.willAnswer(studentId, FakePaymentGateway.active("GPA." + studentId));
        assertThat(subscriptions.handle(new CreateSubscriptionCommand(studentId, null)).isSuccess()).isTrue();
    }

    // ---------- Concurrency ----------

    @Test
    void twoNewPathsAtTheSameTime_onTheFreePlan_onlyOneIsCreated() throws Exception {
        for (int studentId = 1; studentId <= 5; studentId++) {
            int student = studentId;
            List<Result<LearningPath>> results = concurrently(List.of(
                    () -> declare(student, REST_AND_JWT),
                    () -> declare(student, SQL)));

            assertThat(results.stream().filter(Result::isSuccess)).hasSize(1);
            assertThat(results.stream().filter(Result::isFailure).map(Result::error))
                    .containsExactly(LearningPathError.PLAN_LIMIT_REACHED);
            assertThat(paths.countActiveByStudentId(student)).isEqualTo(1);
        }
    }

    @Test
    void twoResumesAtTheSameTime_onTheFreePlan_onlyOneIsActive() throws Exception {
        LearningPath first = declare(1, REST_AND_JWT).value();
        commands.handle(new PauseLearningPathCommand(first.getId(), 1));
        LearningPath second = declare(1, SQL).value();
        commands.handle(new PauseLearningPathCommand(second.getId(), 1));

        List<Result<LearningPath>> results = concurrently(List.of(
                () -> commands.handle(new ResumeLearningPathCommand(first.getId(), 1)),
                () -> commands.handle(new ResumeLearningPathCommand(second.getId(), 1))));

        assertThat(results.stream().filter(Result::isSuccess)).hasSize(1);
        assertThat(paths.countActiveByStudentId(1)).isEqualTo(1);
    }

    // ---------- Downgrade ----------

    @Test
    void expiredSubscription_keepsTheMostRecentProgressActiveAndPausesTheOthers() throws Exception {
        subscribe(1);
        LearningPath oldest = declare(1, REST_AND_JWT).value();
        LearningPath mostRecent = declare(1, SQL).value();
        LearningPath newest = declare(1, HTTP).value();
        assertThat(paths.countActiveByStudentId(1)).isEqualTo(3);
        // The student advanced on the second path most recently, although the third is the newest.
        execute("UPDATE learning_paths SET last_progress_at = now() - interval '9 days' WHERE id = " + oldest.getId());
        execute("UPDATE learning_paths SET last_progress_at = now() - interval '1 hour' WHERE id = "
                + mostRecent.getId());
        execute("UPDATE learning_paths SET last_progress_at = now() - interval '2 days' WHERE id = " + newest.getId());

        // RevenueCat notifies the expiration; the backend reads the state again and finds nothing active.
        gateway.willAnswer(1, PurchaseVerification.inactive());
        subscriptions.handle(new ProcessRevenueCatEventCommand("evt-expiration", "EXPIRATION", "1", "1", List.of(),
                "PRODUCTION"));

        assertThat(queryString("SELECT status FROM subscriptions")).isEqualTo("Expired");
        assertThat(paths.findById(mostRecent.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(paths.findById(oldest.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.PAUSED);
        assertThat(paths.findById(newest.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.PAUSED);
        // Nothing is deleted, and with 3 paths in total the free plan allows no new one.
        assertThat(paths.countByStudentId(1)).isEqualTo(3);
        Result<LearningPath> another = declare(1, "quiero aprender Docker y contenedores");
        assertThat(another.error()).isEqualTo(LearningPathError.PLAN_LIMIT_REACHED);
        assertThat(another.details()).containsEntry("limit", "TotalRoutes");
    }

    @Test
    void expiredSubscription_withinTheFreeLimit_changesNoPath() throws Exception {
        subscribe(1);
        LearningPath only = declare(1, REST_AND_JWT).value();

        gateway.willAnswer(1, PurchaseVerification.inactive());
        subscriptions.handle(new ProcessRevenueCatEventCommand("evt-expiration", "EXPIRATION", "1", "1", List.of(),
                "PRODUCTION"));

        assertThat(paths.findById(only.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.ACTIVE);
    }
}
