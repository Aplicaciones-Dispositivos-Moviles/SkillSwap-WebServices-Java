package com.innovify.skillswap.moderationdisputes.application.eventhandlers;

import com.innovify.skillswap.credentialverification.domain.model.events.CertificateFlaggedSuspicious;
import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.EscalateCertificateReviewCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A certificate was registered as suspicious (US13, US14): it is escalated to a Verificador senior. The escalation
 * is idempotent, so a repeated event opens no second dispute.
 */
public class EscalateCertificateReviewEventHandler implements DomainEventHandler<CertificateFlaggedSuspicious> {

    private static final Logger log = LoggerFactory.getLogger(EscalateCertificateReviewEventHandler.class);

    private final DisputeCommandService commandService;

    public EscalateCertificateReviewEventHandler(DisputeCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(CertificateFlaggedSuspicious event) {
        var result = commandService.handle(new EscalateCertificateReviewCommand(event.certificateId(),
                event.ownerId(), event.reasons()));
        if (result.isFailure()) {
            log.warn("The suspicious certificate {} was not escalated: {}", event.certificateId(), result.error());
        }
    }
}
