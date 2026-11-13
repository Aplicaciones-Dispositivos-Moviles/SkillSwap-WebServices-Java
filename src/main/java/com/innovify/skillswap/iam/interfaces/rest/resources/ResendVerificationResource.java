package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Resource for asking for a new verification email.
 *
 * @param email the institutional email of the account
 */
public record ResendVerificationResource(@NotNull String email) {
}
