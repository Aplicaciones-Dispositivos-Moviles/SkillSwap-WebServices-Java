package com.innovify.skillswap.moderationdisputes.domain.model.queries;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;

/**
 * The disputes assigned to a reviewer, oldest first.
 *
 * @param verifierUserId the reviewer
 * @param status         only the disputes in this state; null for all of them
 */
public record GetDisputesByReviewerQuery(int verifierUserId, DisputeStatus status) {
}
