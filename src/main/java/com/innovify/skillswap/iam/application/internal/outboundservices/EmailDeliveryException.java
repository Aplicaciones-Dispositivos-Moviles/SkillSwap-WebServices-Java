package com.innovify.skillswap.iam.application.internal.outboundservices;

/** The email provider rejected the message or could not be reached. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
