package com.innovify.skillswap.reputation.application.eventhandlers;

import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.commands.RecordCaseResolutionCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordOverturnCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A verifier resolved a case: it counts in their reliability and an approval certifies the skill of the
 * student. When the decision overturned the rejection of another verifier after an appeal, that verifier is
 * discounted as well. Each part is recorded on its own, so one failing does not lose the other.
 */
public class RecordCaseResolutionEventHandler implements DomainEventHandler<VerificationCaseResolved> {

    private static final Logger log = LoggerFactory.getLogger(RecordCaseResolutionEventHandler.class);

    private final ReputationCommandService commandService;

    public RecordCaseResolutionEventHandler(ReputationCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(VerificationCaseResolved event) {
        var resolution = commandService.handle(new RecordCaseResolutionCommand(
                event.verifierUserId(), event.studentId(), event.decision() == ReviewDecision.APPROVED));
        if (resolution.isFailure()) {
            log.warn("The resolution of the case {} was not recorded in the reputation: {}",
                    event.caseId(), resolution.error());
        }

        if (event.overturnedVerifierUserId() != null) {
            var overturn = commandService.handle(new RecordOverturnCommand(event.overturnedVerifierUserId()));
            if (overturn.isFailure()) {
                log.warn("The overturned decision of the case {} was not recorded in the reputation: {}",
                        event.caseId(), overturn.error());
            }
        }
    }
}
