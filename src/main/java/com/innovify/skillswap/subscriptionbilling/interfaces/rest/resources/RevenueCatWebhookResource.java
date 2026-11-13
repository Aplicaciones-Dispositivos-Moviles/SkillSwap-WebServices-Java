package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A webhook notification of RevenueCat. Only the members the backend uses are declared; the others are ignored.
 *
 * @param apiVersion the version of the payload ("1.0")
 * @param event      the event
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueCatWebhookResource(@JsonProperty("api_version") String apiVersion, Event event) {

    /**
     * @param id                the id of the event, the same in every retry
     * @param type              TEST, INITIAL_PURCHASE, RENEWAL, CANCELLATION, UNCANCELLATION, EXPIRATION,
     *                          BILLING_ISSUE, PRODUCT_CHANGE...
     * @param appUserId         the app user id: the id of the student
     * @param originalAppUserId the first app user id of the customer
     * @param aliases           the other app user ids of the customer
     * @param environment       SANDBOX or PRODUCTION
     * @param productId         the store product
     * @param entitlementIds    the entitlements the product grants
     * @param eventTimestampMs  when the event happened (epoch milliseconds)
     * @param expirationAtMs    when the period ends (epoch milliseconds), informational only
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String id, String type,
                        @JsonProperty("app_user_id") String appUserId,
                        @JsonProperty("original_app_user_id") String originalAppUserId,
                        List<String> aliases,
                        String environment,
                        @JsonProperty("product_id") String productId,
                        @JsonProperty("entitlement_ids") List<String> entitlementIds,
                        @JsonProperty("event_timestamp_ms") Long eventTimestampMs,
                        @JsonProperty("expiration_at_ms") Long expirationAtMs) {
    }
}
