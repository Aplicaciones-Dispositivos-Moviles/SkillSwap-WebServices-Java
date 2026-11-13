package com.innovify.skillswap.moderationdisputes.domain.services;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;

/**
 * Domain service: a dispute can only be resolved while it is pending, and only with an outcome that makes sense for
 * its origin. A certificate review or an appeal is upheld or overturned; only a report of a user is dismissed or
 * sanctioned.
 */
public class DisputeResolutionValidator {

    public boolean canResolve(Dispute dispute) {
        return dispute != null && dispute.getStatus() == DisputeStatus.PENDING;
    }

    public boolean isValidOutcome(DisputeSourceType sourceType, DisputeOutcome outcome) {
        if (sourceType == null || outcome == null) {
            return false;
        }
        return switch (sourceType) {
            case CERTIFICATE_REVIEW, VERIFIER_DECISION_APPEAL ->
                    outcome == DisputeOutcome.UPHELD || outcome == DisputeOutcome.OVERTURNED;
            case USER_REPORT -> outcome == DisputeOutcome.DISMISSED || outcome == DisputeOutcome.SANCTIONED;
        };
    }
}
