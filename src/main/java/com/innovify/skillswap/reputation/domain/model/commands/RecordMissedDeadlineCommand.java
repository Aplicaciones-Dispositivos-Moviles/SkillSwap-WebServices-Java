package com.innovify.skillswap.reputation.domain.model.commands;

/**
 * A verifier did not resolve an assigned case within its deadline.
 *
 * @param verifierUserId the verifier who missed it
 */
public record RecordMissedDeadlineCommand(int verifierUserId) {
}
