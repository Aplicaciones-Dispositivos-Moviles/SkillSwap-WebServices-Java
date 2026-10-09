package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Update user full name command.
 *
 * @param userId      the id of the profile being updated
 * @param fullName    the real name of the student; blank or null clears it
 * @param actorUserId the id of the authenticated user performing the update
 */
public record UpdateUserFullNameCommand(int userId, String fullName, int actorUserId) {
}
