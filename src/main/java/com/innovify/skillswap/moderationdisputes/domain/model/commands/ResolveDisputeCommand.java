package com.innovify.skillswap.moderationdisputes.domain.model.commands;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;

/**
 * The assigned reviewer resolves a dispute. A null outcome means the request did not carry a valid one, which the
 * service rejects as InvalidOutcome.
 *
 * @param disputeId        the dispute
 * @param verifierUserId   the reviewer (the authenticated user)
 * @param outcome          the decision
 * @param resolutionNotes the observations of the reviewer, required
 */
public record ResolveDisputeCommand(int disputeId, int verifierUserId, DisputeOutcome outcome,
                                    String resolutionNotes) {
}
