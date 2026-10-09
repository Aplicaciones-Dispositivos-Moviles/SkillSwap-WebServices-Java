package com.innovify.skillswap.moderationdisputes.application.internal.commandservices;

import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;

/** Thrown inside the transaction of a resolution that cannot go on, so everything saved so far is rolled back. */
final class DisputeResolutionAbortedException extends RuntimeException {

    private final transient ModerationDisputesError error;

    DisputeResolutionAbortedException(ModerationDisputesError error) {
        super("The dispute could not be resolved: " + error);
        this.error = error;
    }

    ModerationDisputesError error() {
        return error;
    }
}
