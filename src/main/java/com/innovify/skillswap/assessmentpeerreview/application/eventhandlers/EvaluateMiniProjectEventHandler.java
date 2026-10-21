package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.EvaluateMiniProjectSubmissionCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A student delivered a mini-project (US19): its preliminary evaluation by the AI starts. The delivery was already
 * committed when the event arrives, and the evaluation may take up to a minute, so it runs on the given executor and
 * the student gets the answer of the delivery at once; the app reads the result with the delivery later. When the
 * executor is full the evaluation runs right here, so no delivery is left without one.
 *
 * <p>The command service decides everything else: approve the delivery, or open its verification case.
 */
public class EvaluateMiniProjectEventHandler implements DomainEventHandler<MiniProjectSubmitted>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EvaluateMiniProjectEventHandler.class);

    private final MiniProjectSubmissionCommandService commandService;
    private final Executor executor;

    public EvaluateMiniProjectEventHandler(MiniProjectSubmissionCommandService commandService, Executor executor) {
        this.commandService = commandService;
        this.executor = executor;
    }

    @Override
    public void handle(MiniProjectSubmitted event) {
        try {
            executor.execute(() -> evaluate(event));
        } catch (RejectedExecutionException exception) {
            log.warn("The evaluation of the delivery {} could not be queued; it runs now", event.submissionId());
            evaluate(event);
        }
    }

    private void evaluate(MiniProjectSubmitted event) {
        try {
            var result = commandService.handle(new EvaluateMiniProjectSubmissionCommand(event.submissionId()));
            if (result.isFailure()) {
                log.warn("The delivery {} of the mini-project {} was not evaluated: {}", event.submissionId(),
                        event.miniProjectId(), result.error());
            }
        } catch (RuntimeException exception) {
            // On a background thread nobody else would see it.
            log.error("The evaluation of the delivery {} failed", event.submissionId(), exception);
        }
    }

    /** Stops taking evaluations when the application shuts down; the ones in progress are not interrupted. */
    @Override
    public void close() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }
}
