package com.innovify.skillswap.assessmentpeerreview.infrastructure.scheduling;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerificationCaseCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReassignOverdueCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US39 escenario 2: looks periodically for the assigned cases whose review deadline passed and hands each one to the
 * command service, which records the breach of the verifier and reassigns the case. Each case is locked while it is
 * handled and a case already handled is left as is, so running it again, or on several instances, is safe.
 */
@Component
@ConditionalOnProperty(name = "review-deadlines.reassignment-enabled", havingValue = "true", matchIfMissing = true)
public class OverdueCaseReassignmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueCaseReassignmentScheduler.class);

    private final VerificationCaseRepository caseRepository;
    private final VerificationCaseCommandService commandService;

    public OverdueCaseReassignmentScheduler(VerificationCaseRepository caseRepository,
                                            VerificationCaseCommandService commandService) {
        this.caseRepository = caseRepository;
        this.commandService = commandService;
    }

    @Scheduled(initialDelayString = "${review-deadlines.check-interval:15m}",
            fixedDelayString = "${review-deadlines.check-interval:15m}")
    public void reassignOverdueCases() {
        int failed = 0;
        for (Integer caseId : caseRepository.findOverdueAssignedIds(Instant.now())) {
            if (commandService.handle(new ReassignOverdueCaseCommand(caseId)).isFailure()) {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("{} overdue cases could not be reassigned; they are retried later", failed);
        }
    }
}
