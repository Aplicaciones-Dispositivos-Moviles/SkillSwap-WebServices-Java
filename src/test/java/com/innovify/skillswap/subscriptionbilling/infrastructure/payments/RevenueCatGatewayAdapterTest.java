package com.innovify.skillswap.subscriptionbilling.infrastructure.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatGatewayAdapter;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatSettings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The adapter against a mock RevenueCat API: no network and no real key. */
class RevenueCatGatewayAdapterTest {

    private static final String BASE = "https://api.revenuecat.test/v1/";
    private static final String KEY = "sk_test_secret_key";

    private MockRestServiceServer server;
    private RevenueCatGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        RevenueCatSettings settings = new RevenueCatSettings(KEY, "webhook-secret", "premium", true, 5, 10, BASE);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new RevenueCatGatewayAdapter(RevenueCatGatewayAdapter.configure(builder, settings).build(),
                settings.entitlementId());
    }

    private static String iso(Instant instant) {
        return instant == null ? "null" : "\"" + instant.truncatedTo(ChronoUnit.SECONDS) + "\"";
    }

    /**
     * A customer as GET /v1/subscribers/{app_user_id} returns it, with the shape of the sample of the RevenueCat
     * API reference: a Google Play subscription that grants the "premium" entitlement, an old App Store one with a
     * numeric transaction id, and a non-subscription purchase.
     */
    private static String subscriber(Instant expires, Instant grace, String unsubscribeDetectedAt,
                                     String productIdentifier) {
        return """
                {
                  "request_date": "2026-10-09T17:40:10Z",
                  "request_date_ms": 1791654010884,
                  "subscriber": {
                    "entitlements": {
                      "premium": {
                        "expires_date": %s,
                        "grace_period_expires_date": %s,
                        "product_identifier": "%s",
                        "purchase_date": "2026-09-09T21:52:45Z"
                      },
                      "legacy": {
                        "expires_date": "2020-01-01T00:00:00Z",
                        "grace_period_expires_date": null,
                        "product_identifier": "annual",
                        "purchase_date": "2019-01-01T00:00:00Z"
                      }
                    },
                    "first_seen": "2026-09-01T00:08:41Z",
                    "last_seen": "2026-10-09T17:39:00Z",
                    "management_url": "https://play.google.com/store/account/subscriptions",
                    "non_subscriptions": {
                      "onetime": [
                        {
                          "id": "cadba0c81b",
                          "is_sandbox": true,
                          "purchase_date": "2026-09-04T21:52:45Z",
                          "store": "play_store"
                        }
                      ]
                    },
                    "original_app_user_id": "3",
                    "original_application_version": null,
                    "original_purchase_date": null,
                    "other_purchases": {},
                    "subscriber_attributes": {},
                    "subscriptions": {
                      "premium_monthly:monthly": {
                        "auto_resume_date": null,
                        "billing_issues_detected_at": null,
                        "expires_date": %s,
                        "grace_period_expires_date": %s,
                        "is_sandbox": true,
                        "original_purchase_date": "2026-09-09T21:52:45Z",
                        "ownership_type": "PURCHASED",
                        "period_type": "normal",
                        "purchase_date": "2026-09-09T21:52:45Z",
                        "refunded_at": null,
                        "store": "play_store",
                        "store_transaction_id": "GPA.3346-7012-3456-78901..1",
                        "unsubscribe_detected_at": %s
                      },
                      "annual": {
                        "auto_resume_date": null,
                        "billing_issues_detected_at": null,
                        "expires_date": "2020-01-01T00:00:00Z",
                        "grace_period_expires_date": null,
                        "is_sandbox": false,
                        "original_purchase_date": "2019-01-01T00:00:00Z",
                        "ownership_type": "PURCHASED",
                        "period_type": "normal",
                        "purchase_date": "2019-01-01T00:00:00Z",
                        "refunded_at": null,
                        "store": "app_store",
                        "store_transaction_id": 1000000123456789,
                        "unsubscribe_detected_at": "2019-06-01T00:00:00Z"
                      }
                    }
                  }
                }
                """.formatted(iso(expires), iso(grace), productIdentifier, iso(expires), iso(grace),
                unsubscribeDetectedAt == null ? "null" : "\"" + unsubscribeDetectedAt + "\"");
    }

    private void respondWith(String json) {
        server.expect(requestTo(BASE + "subscribers/3"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    // ---------- verifyPurchase ----------

    @Test
    void verifyPurchase_anActiveEntitlement_isActiveWithItsPeriodTransactionAndRenewal() {
        Instant expires = Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS);
        respondWith(subscriber(expires, null, null, "premium_monthly:monthly"));

        PurchaseVerification verification = adapter.verifyPurchase(3, null);

        server.verify();
        assertThat(verification.active()).isTrue();
        assertThat(verification.productId()).isEqualTo("premium_monthly:monthly");
        assertThat(verification.expiresAt()).isEqualTo(expires);
        assertThat(verification.willRenew()).isTrue();
        assertThat(verification.storeTransactionId()).isEqualTo("GPA.3346-7012-3456-78901..1");
        assertThat(verification.sandbox()).isTrue();
    }

    @Test
    void verifyPurchase_aCancelledSubscription_isActiveButDoesNotRenew() {
        Instant expires = Instant.now().plus(Duration.ofDays(5));
        respondWith(subscriber(expires, null, "2026-10-01T10:00:00Z", "premium_monthly:monthly"));

        PurchaseVerification verification = adapter.verifyPurchase(3, null);

        assertThat(verification.active()).isTrue();
        assertThat(verification.willRenew()).isFalse();
    }

    @Test
    void verifyPurchase_anExpiredEntitlement_isInactive() {
        respondWith(subscriber(Instant.now().minus(Duration.ofDays(1)), null, null, "premium_monthly:monthly"));

        assertThat(adapter.verifyPurchase(3, null).active()).isFalse();
    }

    @Test
    void verifyPurchase_duringTheBillingGracePeriod_isActiveUntilTheGraceEnds() {
        Instant grace = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        respondWith(subscriber(Instant.now().minus(Duration.ofHours(2)), grace, null, "premium_monthly:monthly"));

        PurchaseVerification verification = adapter.verifyPurchase(3, null);

        assertThat(verification.active()).isTrue();
        assertThat(verification.expiresAt()).isEqualTo(grace);
    }

    @Test
    void verifyPurchase_aCustomerWithoutTheEntitlement_isInactive() {
        respondWith("""
                {"request_date":"2026-10-09T17:40:10Z","request_date_ms":1791654010884,
                 "subscriber":{"entitlements":{},"first_seen":"2026-10-09T17:40:10Z","management_url":null,
                 "non_subscriptions":{},"original_app_user_id":"3","other_purchases":{},"subscriptions":{}}}
                """);

        assertThat(adapter.verifyPurchase(3, null).active()).isFalse();
    }

    @Test
    void verifyPurchase_aLifetimeGrantWithoutExpiration_isNotTheMonthlyPlan() {
        respondWith(subscriber(null, null, null, "premium_monthly:monthly"));

        assertThat(adapter.verifyPurchase(3, null).active()).isFalse();
    }

    @Test
    void verifyPurchase_matchesTheProductOrItsBasePlans() {
        Instant expires = Instant.now().plus(Duration.ofDays(20));
        respondWith(subscriber(expires, null, null, "premium_monthly:monthly"));
        respondWith(subscriber(expires, null, null, "premium_monthly:monthly"));
        respondWith(subscriber(expires, null, null, "premium_monthly:monthly"));

        assertThat(adapter.verifyPurchase(3, "premium_monthly").active()).isTrue();
        assertThat(adapter.verifyPurchase(3, "premium_monthly:monthly").active()).isTrue();
        assertThat(adapter.verifyPurchase(3, "premium_yearly").active()).isFalse();
    }

    @Test
    void verifyPurchase_whenRevenueCatFails_throwsPaymentGatewayExceptionWithoutTheKey() {
        server.expect(requestTo(BASE + "subscribers/3")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.verifyPurchase(3, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining(KEY);
    }

    @Test
    void verifyPurchase_withAWrongKey_throwsPaymentGatewayException() {
        server.expect(requestTo(BASE + "subscribers/3")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.verifyPurchase(3, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("401");
    }

    @Test
    void verifyPurchase_whenRevenueCatDoesNotAnswer_throwsPaymentGatewayException() {
        server.expect(requestTo(BASE + "subscribers/3")).andRespond(request -> {
            throw new IOException("Read timed out");
        });

        assertThatThrownBy(() -> adapter.verifyPurchase(3, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("did not answer in time");
    }

    @Test
    void verifyPurchase_anAnswerThatIsNotJson_throwsPaymentGatewayException() {
        respondWith("<html>maintenance</html>");

        assertThatThrownBy(() -> adapter.verifyPurchase(3, null)).isInstanceOf(PaymentGatewayException.class);
    }

    // ---------- cancelRenewal ----------

    @Test
    void cancelRenewal_callsTheGooglePlayCancelEndpointOfTheTransaction() {
        server.expect(requestTo(BASE + "subscribers/3/subscriptions/GPA.3346-7012-3456-78901..1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.cancelRenewal(3, "GPA.3346-7012-3456-78901..1");

        server.verify();
    }

    @Test
    void cancelRenewal_withoutTransaction_throwsPaymentGatewayException() {
        assertThatThrownBy(() -> adapter.cancelRenewal(3, null)).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void cancelRenewal_whenRevenueCatFails_throwsPaymentGatewayException() {
        server.expect(requestTo(BASE + "subscribers/3/subscriptions/GPA.1/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.cancelRenewal(3, "GPA.1")).isInstanceOf(PaymentGatewayException.class);
    }

    // ---------- Settings ----------

    @Test
    void settings_neverPrintTheSecrets() {
        RevenueCatSettings settings = new RevenueCatSettings(KEY, "webhook-secret", null, true, 5, 10, BASE);

        assertThat(settings.toString()).doesNotContain(KEY).doesNotContain("webhook-secret");
        assertThat(settings.entitlementId()).isEqualTo("premium");
    }

    @Test
    void settings_blankValuesMeanNotConfigured() {
        RevenueCatSettings settings = new RevenueCatSettings(" ", "", "premium", true, 5, 10,
                "https://api.revenuecat.com/v1");

        assertThat(settings.hasApiKey()).isFalse();
        assertThat(settings.webhookAuthorization()).isNull();
        assertThat(settings.baseUrl()).isEqualTo("https://api.revenuecat.com/v1/");
    }

    @Test
    void settings_withoutPositiveTimeouts_areRejected() {
        assertThatThrownBy(() -> new RevenueCatSettings(KEY, null, "premium", true, 0, 10, BASE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
