package com.innovify.skillswap.iam.application.internal.outboundservices;

/**
 * Sends transactional emails, decoupling the application from the provider (an HTTP email API in production,
 * because the hosting blocks outbound SMTP).
 */
public interface EmailSender {

    /**
     * Sends the message right away.
     *
     * @throws EmailDeliveryException when the provider rejects the message or cannot be reached
     */
    void send(EmailMessage message);
}
