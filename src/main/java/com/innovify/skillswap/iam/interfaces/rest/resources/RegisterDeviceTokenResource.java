package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Resource for registering the device that receives the push notifications.
 *
 * @param token the Firebase Cloud Messaging registration token of the device (up to 512 characters)
 */
public record RegisterDeviceTokenResource(@NotNull String token) {
}
