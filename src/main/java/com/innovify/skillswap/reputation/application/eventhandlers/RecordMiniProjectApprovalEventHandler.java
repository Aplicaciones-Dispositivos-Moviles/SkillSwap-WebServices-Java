package com.innovify.skillswap.reputation.application.eventhandlers;

import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectApprovedByAi;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The AI approved the mini-project of a student: the skill counts in their employability like any other approval. */
public class RecordMiniProjectApprovalEventHandler implements DomainEventHandler<MiniProjectApprovedByAi> {

    private static final Logger log = LoggerFactory.getLogger(RecordMiniProjectApprovalEventHandler.class);

    private final ReputationCommandService commandService;

    public RecordMiniProjectApprovalEventHandler(ReputationCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(MiniProjectApprovedByAi event) {
        var result = commandService.handle(new RecordAutomaticApprovalCommand(event.studentId()));
        if (result.isFailure()) {
            log.warn("The approval of the delivery {} was not recorded in the reputation: {}",
                    event.submissionId(), result.error());
        }
    }
}
