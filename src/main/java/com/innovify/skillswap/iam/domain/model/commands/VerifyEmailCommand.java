package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Verify email command: confirms the institutional email with the token of the verification link.
 *
 * @param token the token received by email, as it is in the link
 */
public record VerifyEmailCommand(String token) {
}
