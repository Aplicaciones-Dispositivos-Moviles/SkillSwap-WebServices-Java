package com.innovify.skillswap.subscriptionbilling.domain.services;

/** The payment gateway could not answer or refused the request. Its secrets are never part of the message. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
