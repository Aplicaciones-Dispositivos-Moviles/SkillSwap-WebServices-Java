package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Register device token command.
 *
 * @param userId the authenticated user
 * @param token  the Firebase Cloud Messaging registration token of the device
 */
public record RegisterDeviceTokenCommand(int userId, String token) {
}
