package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Resource for signing in.
 *
 * @param username the username
 * @param password the plain-text password
 */
public record SignInResource(@NotNull String username, @NotNull String password) {
}
