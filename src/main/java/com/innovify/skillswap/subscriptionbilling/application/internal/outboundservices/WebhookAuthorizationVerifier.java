package com.innovify.skillswap.subscriptionbilling.application.internal.outboundservices;

/**
 * Checks the Authorization header of a webhook notification against the secret shared with the gateway, so only
 * the gateway can notify the backend.
 */
public interface WebhookAuthorizationVerifier {

    /** False when the header is missing, wrong, or no secret is configured. */
    boolean isAuthorized(String authorizationHeader);
}
