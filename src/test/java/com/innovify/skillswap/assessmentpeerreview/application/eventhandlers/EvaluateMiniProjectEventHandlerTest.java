package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectEvaluationOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.EvaluateMiniProjectSubmissionCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.shared.application.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EvaluateMiniProjectEventHandlerTest {

    private static final MiniProjectSubmitted EVENT = new MiniProjectSubmitted(5, 3, 1, 10, "http-basics",
            "https://github.com/ana/http-basics", Instant.now());

    /** Records the evaluations it is asked for, and the thread that asked. */
    private static final class RecordingCommandService implements MiniProjectSubmissionCommandService {

        final List<Integer> evaluated = new ArrayList<>();
        final List<String> threads = new ArrayList<>();
        RuntimeException failure;
        Result<MiniProjectEvaluationOutcome> answer = Result.failure(AssessmentPeerReviewError.NONE, "");

        @Override
        public Result<MiniProjectSubmission> handle(SubmitMiniProjectCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized Result<MiniProjectEvaluationOutcome> handle(EvaluateMiniProjectSubmissionCommand command) {
            evaluated.add(command.submissionId());
            threads.add(Thread.currentThread().getName());
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    private final RecordingCommandService commandService = new RecordingCommandService();

    @Test
    void handle_evaluatesTheDeliveryOfTheEvent() {
        new EvaluateMiniProjectEventHandler(commandService, Runnable::run).handle(EVENT);

        assertThat(commandService.evaluated).containsExactly(5);
    }

    @Test
    void handle_runsTheEvaluationOnTheExecutor_soTheDeliveryIsAnsweredAtOnce() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "evaluator"));
        EvaluateMiniProjectEventHandler handler = new EvaluateMiniProjectEventHandler(commandService, executor);

        handler.handle(EVENT);
        handler.close();

        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(commandService.evaluated).containsExactly(5);
        assertThat(commandService.threads).containsExactly("evaluator");
    }

    @Test
    void handle_whenTheExecutorIsFull_evaluatesInTheCallingThread() {
        EvaluateMiniProjectEventHandler handler = new EvaluateMiniProjectEventHandler(commandService, runnable -> {
            throw new RejectedExecutionException("full");
        });

        handler.handle(EVENT);

        assertThat(commandService.evaluated).containsExactly(5);
        assertThat(commandService.threads).containsExactly(Thread.currentThread().getName());
    }

    @Test
    void handle_aFailedOrThrowingEvaluation_isOnlyLogged() {
        EvaluateMiniProjectEventHandler handler = new EvaluateMiniProjectEventHandler(commandService, Runnable::run);
        commandService.answer = Result.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_EVALUATED, "done");
        assertThatCode(() -> handler.handle(EVENT)).doesNotThrowAnyException();

        commandService.failure = new IllegalStateException("boom");
        assertThatCode(() -> handler.handle(EVENT)).doesNotThrowAnyException();
        assertThat(commandService.evaluated).containsExactly(5, 5);
    }

    @Test
    void close_withAnExecutorThatIsNotAPool_doesNothing() {
        assertThatCode(() -> new EvaluateMiniProjectEventHandler(commandService, Runnable::run).close())
                .doesNotThrowAnyException();
    }
}
