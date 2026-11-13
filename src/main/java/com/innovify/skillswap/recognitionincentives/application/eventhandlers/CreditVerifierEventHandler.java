package com.innovify.skillswap.recognitionincentives.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A verifier resolved a case, approving or rejecting it: their wallet is credited. It is the only place where
 * credits are earned and it depends only on that event, so no user can give credits to another. The type of the
 * case is translated here into the one of this context, which sets the amount.
 */
public class CreditVerifierEventHandler implements DomainEventHandler<VerificationCaseResolved> {

    private static final Logger log = LoggerFactory.getLogger(CreditVerifierEventHandler.class);

    private final WalletCommandService commandService;

    public CreditVerifierEventHandler(WalletCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(VerificationCaseResolved event) {
        var result = commandService.handle(new CreditVerifierCommand(event.verifierUserId(), event.caseId(),
                toResolvedCaseType(event.caseType())));
        if (result.isFailure()) {
            log.warn("The case {} was not credited to the verifier {}: {}", event.caseId(),
                    event.verifierUserId(), result.error());
        }
    }

    private static ResolvedCaseType toResolvedCaseType(CaseType caseType) {
        if (caseType == null) {
            return null;
        }
        return switch (caseType) {
            case QUIZ -> ResolvedCaseType.QUIZ;
            case MINI_PROJECT -> ResolvedCaseType.MINI_PROJECT;
        };
    }
}
