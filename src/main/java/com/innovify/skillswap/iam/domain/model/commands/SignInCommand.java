package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Sign in command.
 *
 * @param username the username
 * @param password the plain-text password
 */
public record SignInCommand(String username, String password) {
}
