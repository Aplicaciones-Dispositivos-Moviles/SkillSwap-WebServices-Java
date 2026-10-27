package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;

/**
 * The student moves a scheduled demonstration.
 *
 * @param scheduledAt the new date and time (ISO-8601 in UTC, at least 24 hours ahead)
 */
public record RescheduleFinalDemonstrationResource(Instant scheduledAt) {
}
