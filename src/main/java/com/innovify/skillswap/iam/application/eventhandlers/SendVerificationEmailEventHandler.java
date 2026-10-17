package com.innovify.skillswap.iam.application.eventhandlers;

import com.innovify.skillswap.iam.application.internal.emails.VerificationEmailComposer;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailDeliveryException;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the verification email once the token is saved. The publisher runs it after the change is committed, so
 * an email is never sent for a token that was not stored. A delivery failure is logged (WARN here, ERROR with the
 * answer of the provider in the sender) and does not undo the sign-up: the student gets a new email when trying to
 * sign in or asking for it again.
 */
public class SendVerificationEmailEventHandler implements DomainEventHandler<EmailVerificationRequested> {

    private static final Logger log = LoggerFactory.getLogger(SendVerificationEmailEventHandler.class);

    private final EmailSender emailSender;
    private final VerificationEmailComposer composer;

    public SendVerificationEmailEventHandler(EmailSender emailSender, VerificationEmailComposer composer) {
        this.emailSender = emailSender;
        this.composer = composer;
    }

    @Override
    public void handle(EmailVerificationRequested event) {
        try {
            emailSender.send(composer.compose(event.username(), event.email(), event.token(), event.issuedAt(),
                    event.expiresAt()));
        } catch (EmailDeliveryException exception) {
            // The sender already logged the status and the body of the answer of the provider.
            log.warn("The user {} ({}) was created but the verification email could not be sent: {}",
                    event.userId(), event.email(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("The verification email of the user {} ({}) could not be prepared", event.userId(),
                    event.email(), exception);
        }
    }
}
