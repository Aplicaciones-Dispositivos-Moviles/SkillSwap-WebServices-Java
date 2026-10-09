package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to a webhook notification. RevenueCat only looks at the status code.
 *
 * @param outcome Processed, Duplicate (a retry of an event already applied) or Ignored (a TEST event, or an event
 *                that does not belong to a student)
 */
public record WebhookAcknowledgementResource(
        @Schema(allowableValues = {"Processed", "Duplicate", "Ignored"}) String outcome) {
}
