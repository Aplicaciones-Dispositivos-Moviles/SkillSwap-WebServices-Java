package com.innovify.skillswap.iam.domain.model.commands;

import java.util.List;

/**
 * Update interest profile command (US04).
 *
 * @param userId      the id of the profile being updated
 * @param topics      the interest topics; they replace the previous ones
 * @param description the profile description (bio); null keeps the current one
 * @param actorUserId the id of the authenticated user performing the update
 */
public record UpdateInterestProfileCommand(int userId, List<String> topics, String description, int actorUserId) {
}
