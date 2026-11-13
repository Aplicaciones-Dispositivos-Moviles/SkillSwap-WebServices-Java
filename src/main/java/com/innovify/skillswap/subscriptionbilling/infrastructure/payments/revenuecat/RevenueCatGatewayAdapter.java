package com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.shared.infrastructure.json.JsonException;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link PaymentGateway} on top of the RevenueCat REST API v1, which validates the purchases with Google Play
 * Billing. RevenueCat is not a payment processor: Google Play charges the student, and RevenueCat reports the
 * state of the customer, whose app user id is the id of the student.
 *
 * <p>The state is derived from the configured entitlement ({@code subscriber.entitlements.<id>}): it grants the
 * paid plan while its {@code expires_date}, or its {@code grace_period_expires_date} during a billing grace period,
 * is in the future. The subscription of the product that grants it gives the store transaction and whether it
 * renews ({@code unsubscribe_detected_at} is set once the student cancelled). The API key is a header of the
 * client and is never logged.
 */
public class RevenueCatGatewayAdapter implements PaymentGateway {

    static final String SUBSCRIBER_PATH = "subscribers/{appUserId}";
    static final String CANCEL_PATH = "subscribers/{appUserId}/subscriptions/{storeTransactionId}/cancel";

    private final RestClient restClient;
    private final String entitlementId;

    public RevenueCatGatewayAdapter(RestClient restClient, String entitlementId) {
        this.restClient = restClient;
        this.entitlementId = entitlementId;
    }

    /**
     * Sets the base URL and the authentication of the client. The request factory (with the timeouts) is left to
     * the caller, so a test can bind a mock server to the same builder.
     */
    public static RestClient.Builder configure(RestClient.Builder builder, RevenueCatSettings settings) {
        return builder
                .baseUrl(settings.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    public PurchaseVerification verifyPurchase(int studentId, String productId) {
        String body;
        try {
            body = restClient.get()
                    .uri(SUBSCRIBER_PATH, String.valueOf(studentId))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw unavailable("read the customer", exception);
        }

        try {
            return toVerification(body, productId, Instant.now());
        } catch (JsonException | ClassCastException | DateTimeParseException exception) {
            throw new PaymentGatewayException("RevenueCat answered a customer that could not be read.", exception);
        }
    }

    @Override
    public void cancelRenewal(int studentId, String storeTransactionId) {
        if (storeTransactionId == null || storeTransactionId.isBlank()) {
            throw new PaymentGatewayException("The subscription has no store transaction to cancel.");
        }
        try {
            restClient.post()
                    .uri(CANCEL_PATH, String.valueOf(studentId), storeTransactionId.strip())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable("cancel the renewals", exception);
        }
    }

    /** Reads the answer of GET /subscribers/{app_user_id}. */
    PurchaseVerification toVerification(String body, String productId, Instant now) {
        Map<?, ?> subscriber = object(object(Json.parse(body == null ? "" : body)).get("subscriber"));
        Map<?, ?> entitlement = object(object(subscriber.get("entitlements")).get(entitlementId));
        if (entitlement.isEmpty()) {
            return PurchaseVerification.inactive();
        }

        String product = text(entitlement.get("product_identifier"));
        Instant expiresAt = latest(instant(entitlement.get("expires_date")),
                instant(entitlement.get("grace_period_expires_date")));
        // A missing expiration is a lifetime grant, which is not a subscription of the monthly plan.
        if (product == null || expiresAt == null || !expiresAt.isAfter(now) || !isProduct(product, productId)) {
            return PurchaseVerification.inactive();
        }

        Map<?, ?> subscription = object(object(subscriber.get("subscriptions")).get(product));
        boolean willRenew = !subscription.isEmpty() && subscription.get("unsubscribe_detected_at") == null
                && subscription.get("refunded_at") == null;
        return new PurchaseVerification(true, product, expiresAt, willRenew,
                text(subscription.get("store_transaction_id")), Boolean.TRUE.equals(subscription.get("is_sandbox")));
    }

    /**
     * Google Play products are reported as "product:base-plan", so the product the app knows matches its base
     * plans too.
     */
    private static boolean isProduct(String reported, String expected) {
        return expected == null || reported.equals(expected) || reported.startsWith(expected + ":");
    }

    private static PaymentGatewayException unavailable(String action, RestClientException exception) {
        String reason;
        if (exception instanceof RestClientResponseException response) {
            reason = "it answered " + response.getStatusCode().value();
        } else if (exception instanceof ResourceAccessException) {
            reason = "it did not answer in time";
        } else {
            reason = "the request failed";
        }
        // The message never includes the headers, so the API key cannot leak through it.
        return new PaymentGatewayException("RevenueCat could not " + action + ": " + reason + ".", exception);
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    /** The store transaction id is a string for Google Play and a number for the App Store. */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private static Instant instant(Object value) {
        String text = text(value);
        return text == null ? null : Instant.parse(text);
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }
}
