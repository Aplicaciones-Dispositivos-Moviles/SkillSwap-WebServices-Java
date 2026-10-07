package com.innovify.skillswap.iam.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Opaque identifier of the user's mobile device. Reserved as an extension point for a future
 * push-notification integration (provider still to be defined).
 */
public record DeviceToken(String value) {

    public DeviceToken {
        if (value == null || value.isBlank()) {
            throw new DomainException("The device token cannot be empty.");
        }
        value = value.strip();
    }
}
