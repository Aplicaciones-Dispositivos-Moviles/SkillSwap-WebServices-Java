package com.innovify.skillswap.reputation.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseDeadlineMissed;
import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.commands.RecordMissedDeadlineCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A verifier did not resolve an assigned case within its deadline (US39 escenario 2): the breach is recorded in their
 * reliability. Assessment &amp; Peer Review publishes it once per assignment.
 */
public class RecordMissedDeadlineEventHandler implements DomainEventHandler<VerificationCaseDeadlineMissed> {

    private static final Logger log = LoggerFactory.getLogger(RecordMissedDeadlineEventHandler.class);

    private final ReputationCommandService commandService;

    public RecordMissedDeadlineEventHandler(ReputationCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(VerificationCaseDeadlineMissed event) {
        var result = commandService.handle(new RecordMissedDeadlineCommand(event.verifierUserId()));
        if (result.isFailure()) {
            log.warn("The missed deadline of the case {} was not recorded in the reputation: {}", event.caseId(),
                    result.error());
        }
    }
}
