package com.innovify.skillswap.iam.application.internal.outboundservices;

/** What the push provider answered. */
public enum PushDeliveryResult {
    /** Accepted for delivery. */
    SENT,
    /** The token is no longer valid (app uninstalled, token rotated): it must be forgotten. */
    INVALID_TOKEN,
    /** A temporary or unexpected failure: the token is kept. */
    FAILED
}
