package com.innovify.skillswap.shared.interfaces.rest.resources;

import java.time.Instant;

/** Healthy while the service is running; timestamp is when the check was answered (UTC). */
public record HealthResource(String status, Instant timestamp) {
}
