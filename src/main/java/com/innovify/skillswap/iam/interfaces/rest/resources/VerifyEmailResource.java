package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Resource for verifying the institutional email.
 *
 * @param token the token of the verification link
 */
public record VerifyEmailResource(@NotNull String token) {
}
