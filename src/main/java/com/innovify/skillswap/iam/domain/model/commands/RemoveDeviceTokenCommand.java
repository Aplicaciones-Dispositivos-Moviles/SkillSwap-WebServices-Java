package com.innovify.skillswap.iam.domain.model.commands;

/**
 * Remove device token command: the student denied the notification permission or signed out of the device.
 *
 * @param userId the authenticated user
 */
public record RemoveDeviceTokenCommand(int userId) {
}
