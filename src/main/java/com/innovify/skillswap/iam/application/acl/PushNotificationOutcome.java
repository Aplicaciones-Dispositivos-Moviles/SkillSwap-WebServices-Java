package com.innovify.skillswap.iam.application.acl;

/** Outcome of asking Identity &amp; Access to notify a user on their device. */
public enum PushNotificationOutcome {
    /** Handed to the push provider. */
    SENT,
    /** The user has no device token: they did not grant (or revoked) the notification permission. */
    NO_DEVICE_TOKEN,
    /** The provider reported the token as no longer valid; it was forgotten. */
    INVALID_DEVICE_TOKEN,
    USER_NOT_FOUND,
    FAILED
}
