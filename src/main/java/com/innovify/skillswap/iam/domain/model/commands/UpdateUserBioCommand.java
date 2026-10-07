package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Update user bio command.
 *
 * @param userId      the id of the profile being updated
 * @param bio         the new bio text
 * @param actorUserId the id of the authenticated user performing the update
 */
public record UpdateUserBioCommand(int userId, String bio, int actorUserId) {
}
