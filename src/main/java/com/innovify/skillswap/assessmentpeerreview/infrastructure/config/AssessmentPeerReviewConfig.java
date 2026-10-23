package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.eventhandlers.EvaluateMiniProjectEventHandler;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.ai.GeminiMiniProjectEvaluator;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.startup.PendingEvaluationRecovery;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiClient;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the Assessment &amp; Peer Review beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties({ReviewDeadlineSettings.class, MiniProjectEvaluationSettings.class})
public class AssessmentPeerReviewConfig {

    /** How many preliminary evaluations run at the same time. */
    static final int EVALUATION_THREADS = 2;
    /** How many deliveries can wait for an evaluation before the next one runs in the thread that delivered it. */
    static final int EVALUATION_QUEUE_CAPACITY = 100;

    @Bean
    public VerifierMatcher verifierMatcher() {
        return new DefaultVerifierMatcher();
    }

    /**
     * The preliminary evaluation of the mini-projects with Gemini (the same client, key and models as the question
     * generator). With mini-project-evaluation.enabled=false nothing is sent to Gemini and every delivery goes to a
     * verifier, as when Gemini fails.
     */
    @Bean
    public MiniProjectEvaluationService miniProjectEvaluationService(GeminiClient geminiClient,
                                                                     MiniProjectEvaluationSettings settings,
                                                                     @Value("${gemini.content-language:Spanish}") String contentLanguage) {
        if (!settings.enabled()) {
            return (brief, repositoryUrl) -> {
                throw new IllegalStateException(
                        "The preliminary evaluation is disabled (mini-project-evaluation.enabled=false).");
            };
        }
        return new GeminiMiniProjectEvaluator(geminiClient, Duration.ofSeconds(settings.timeoutSeconds()),
                contentLanguage);
    }

    /**
     * Starts the preliminary evaluation of each delivered mini-project. The evaluations run on a small pool of their
     * own (two at a time, up to {@value #EVALUATION_QUEUE_CAPACITY} waiting), so a slow answer of Gemini never holds
     * the request of the student; with mini-project-evaluation.asynchronous=false (tests) they run in the same thread.
     * The pool is shut down with the handler.
     */
    @Bean
    public DomainEventHandler<MiniProjectSubmitted> evaluateMiniProjectEventHandler(
            MiniProjectSubmissionCommandService commandService, MiniProjectEvaluationSettings settings) {
        Executor executor = settings.asynchronous() ? newEvaluationExecutor() : Runnable::run;
        return new EvaluateMiniProjectEventHandler(commandService, executor);
    }

    /** Retries the evaluations that a restart interrupted, once the application is ready. */
    @Bean
    public PendingEvaluationRecovery pendingEvaluationRecovery(MiniProjectSubmissionRepository submissions,
                                                               DomainEventPublisher eventPublisher) {
        return new PendingEvaluationRecovery(submissions, eventPublisher);
    }

    private static ExecutorService newEvaluationExecutor() {
        AtomicInteger count = new AtomicInteger();
        return new ThreadPoolExecutor(EVALUATION_THREADS, EVALUATION_THREADS, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(EVALUATION_QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "mini-project-evaluation-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
