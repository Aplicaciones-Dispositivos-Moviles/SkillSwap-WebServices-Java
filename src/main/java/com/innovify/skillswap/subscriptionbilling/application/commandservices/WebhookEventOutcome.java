package com.innovify.skillswap.subscriptionbilling.application.commandservices;

/** What happened with a webhook notification. All of them are acknowledged to the gateway with a 200. */
public enum WebhookEventOutcome {
    /** The state of the student was read from the gateway and applied. */
    PROCESSED,
    /** The event was already processed: a retry of the gateway. */
    DUPLICATE,
    /** A test event, an event of a disallowed environment or of a user that is not a student of the platform. */
    IGNORED
}
