package com.innovify.skillswap.learningpathengine.application.eventhandlers;

import com.innovify.skillswap.credentialverification.domain.model.events.CertificateVerified;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RecognizeValidatedCertificateCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * A verifier validated a certificate of a student (Credential Verification): the pending nodes of their paths
 * whose skill it covers are completed and linked to it, and the rest of the path is recalculated (the nodes they
 * unlock become available); completed nodes are kept as they are. The event arrives after the commit of the
 * certificate, so the paths change in a transaction of their own.
 */
public class RecognizeValidatedCertificateEventHandler implements DomainEventHandler<CertificateVerified> {

    private static final Logger log = LoggerFactory.getLogger(RecognizeValidatedCertificateEventHandler.class);

    private final LearningPathCommandService commandService;
    private final TransactionOperations newTransaction;

    /** @param newTransaction must start a new transaction (REQUIRES_NEW) */
    public RecognizeValidatedCertificateEventHandler(LearningPathCommandService commandService,
                                                     TransactionOperations newTransaction) {
        this.commandService = commandService;
        this.newTransaction = newTransaction;
    }

    @Override
    public void handle(CertificateVerified event) {
        var result = newTransaction.execute(status -> commandService.handle(
                new RecognizeValidatedCertificateCommand(event.ownerId(), event.certificateId())));
        if (result == null || result.isFailure()) {
            log.warn("The validated certificate {} of student {} was not recognized in their paths: {}",
                    event.certificateId(), event.ownerId(), result == null ? null : result.error());
        } else if (!result.value().isEmpty()) {
            log.info("The validated certificate {} completed nodes in {} paths of student {}", event.certificateId(),
                    result.value().size(), event.ownerId());
        }
    }
}
