package com.innovify.skillswap.assessmentpeerreview.infrastructure.startup;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Evaluates again the deliveries that were left without an evaluation. The evaluation runs in the background right
 * after the delivery is saved, so a restart or a crash in between leaves the delivery as Pending forever. When the
 * application is ready, every Pending delivery is announced again with {@link MiniProjectSubmitted}, oldest first, and
 * the usual handler evaluates it. If another evaluation settled it in the meantime, the lock of the delivery makes
 * the second one do nothing.
 *
 * <p>A delivery the AI cannot evaluate is not Pending: it goes to a verifier. So only an interrupted evaluation is
 * retried, and there is no periodic job.
 */
public class PendingEvaluationRecovery implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PendingEvaluationRecovery.class);

    private final MiniProjectSubmissionRepository submissions;
    private final DomainEventPublisher eventPublisher;

    public PendingEvaluationRecovery(MiniProjectSubmissionRepository submissions,
                                     DomainEventPublisher eventPublisher) {
        this.submissions = submissions;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        recover();
    }

    /**
     * Announces again each delivery that has no evaluation yet. It never throws: a failure with one delivery is logged
     * and the others go on, and the application keeps starting.
     *
     * @return how many deliveries were announced
     */
    public int recover() {
        List<MiniProjectSubmission> pending;
        try {
            pending = submissions.findAwaitingEvaluation();
        } catch (RuntimeException exception) {
            log.error("Could not look for the deliveries without an evaluation", exception);
            return 0;
        }

        int announced = 0;
        for (MiniProjectSubmission submission : pending) {
            try {
                eventPublisher.publish(new MiniProjectSubmitted(submission.getId(), submission.getMiniProjectId(),
                        submission.getStudentId(), submission.getPathNodeId(), submission.getSkillTag(),
                        submission.getRepositoryUrl(), submission.getSubmittedAt()));
                announced++;
            } catch (RuntimeException exception) {
                log.error("Could not retry the evaluation of the delivery {}", submission.getId(), exception);
            }
        }
        if (!pending.isEmpty()) {
            log.info("Retried the evaluation of {} of {} deliveries that had none", announced, pending.size());
        }
        return announced;
    }
}
