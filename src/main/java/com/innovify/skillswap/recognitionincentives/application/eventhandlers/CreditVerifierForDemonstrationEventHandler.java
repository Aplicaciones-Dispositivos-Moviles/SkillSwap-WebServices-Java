package com.innovify.skillswap.recognitionincentives.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationReviewed;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierForDemonstrationCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A verifier reviewed a final demonstration, approving or rejecting it: their wallet is credited, as with a
 * resolved case. It depends only on that event, so no user can give credits to another.
 */
public class CreditVerifierForDemonstrationEventHandler implements DomainEventHandler<FinalDemonstrationReviewed> {

    private static final Logger log = LoggerFactory.getLogger(CreditVerifierForDemonstrationEventHandler.class);

    private final WalletCommandService commandService;

    public CreditVerifierForDemonstrationEventHandler(WalletCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(FinalDemonstrationReviewed event) {
        var result = commandService.handle(
                new CreditVerifierForDemonstrationCommand(event.verifierUserId(), event.demonstrationId()));
        if (result.isFailure()) {
            log.warn("The demonstration {} was not credited to the verifier {}: {}", event.demonstrationId(),
                    event.verifierUserId(), result.error());
        }
    }
}
