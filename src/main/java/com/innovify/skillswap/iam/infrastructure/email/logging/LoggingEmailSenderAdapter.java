package com.innovify.skillswap.iam.infrastructure.email.logging;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EmailSender} used when no Brevo API key is configured (local development, tests, demos): nothing is
 * sent, the plain-text version of the email is written to the log instead, so the verification link can be
 * copied from there.
 */
public class LoggingEmailSenderAdapter implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderAdapter.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email NOT sent (BREVO_API_KEY is not set). To: {} | Subject: {}%n{}".formatted(
                message.toAddress(), message.subject(), message.textContent()));
    }
}
