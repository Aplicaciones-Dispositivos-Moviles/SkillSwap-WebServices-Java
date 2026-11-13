package com.innovify.skillswap.reputation.application.eventhandlers;

import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A student passed an assessment on their own: the skill counts in their employability. */
public class RecordAutomaticApprovalEventHandler implements DomainEventHandler<AssessmentAttemptPassed> {

    private static final Logger log = LoggerFactory.getLogger(RecordAutomaticApprovalEventHandler.class);

    private final ReputationCommandService commandService;

    public RecordAutomaticApprovalEventHandler(ReputationCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(AssessmentAttemptPassed event) {
        var result = commandService.handle(new RecordAutomaticApprovalCommand(event.studentId()));
        if (result.isFailure()) {
            log.warn("The approval of the attempt {} was not recorded in the reputation: {}",
                    event.attemptId(), result.error());
        }
    }
}
