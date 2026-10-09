package com.innovify.skillswap.reputation.domain.model.commands;

/**
 * An appeal overturned the rejection of a verifier.
 *
 * @param verifierUserId the verifier whose decision was overturned
 */
public record RecordOverturnCommand(int verifierUserId) {
}
