package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Resource for updating the profile description.
 *
 * @param bio the new bio text (up to 1000 characters)
 */
public record UpdateUserBioResource(@NotNull String bio) {
}
