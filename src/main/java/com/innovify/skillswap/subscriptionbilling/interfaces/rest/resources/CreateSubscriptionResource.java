package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Sent by the app right after the student completed the purchase with the RevenueCat SDK. Nothing in it is
 * trusted: the backend asks RevenueCat for the real state of the authenticated student. Any other member (a token
 * or a transaction of the store) is ignored.
 *
 * @param productId the store product bought (optional; any product that grants the paid plan is accepted)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSubscriptionResource(String productId) {
}
