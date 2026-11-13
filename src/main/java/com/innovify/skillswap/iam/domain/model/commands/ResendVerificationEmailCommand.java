package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Resend verification email command.
 *
 * @param email the institutional email of the account that has not been verified yet
 */
public record ResendVerificationEmailCommand(String email) {
}
