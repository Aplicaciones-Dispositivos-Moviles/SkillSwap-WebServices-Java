package com.innovify.skillswap.subscriptionbilling.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakePaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The subscription API and the RevenueCat webhook end to end, with a fake gateway that plays RevenueCat. The
 * webhook needs no token but its own Authorization secret.
 */
class SubscriptionBillingApiIntegrationTest extends PostgresIntegrationTest {

    private static final String SUBSCRIPTIONS = "/api/v1/subscriptions";
    private static final String WEBHOOK = "/api/v1/subscriptions/webhooks/revenuecat";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private PaymentGateway paymentGateway;

    private MockMvc mockMvc;
    private FakePaymentGateway gateway;
    private User ana;
    private String anaToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        gateway = (FakePaymentGateway) paymentGateway;
        gateway.reset();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        User bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
    }

    // ---------- Helpers ----------

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult webhook(String authorization, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(json);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** A notification with the shape RevenueCat sends; the members the backend ignores are included on purpose. */
    private static String event(String id, String type, String appUserId) {
        return """
                {
                  "api_version": "1.0",
                  "event": {
                    "id": "%s",
                    "type": "%s",
                    "app_user_id": "%s",
                    "original_app_user_id": "%s",
                    "aliases": ["%s"],
                    "event_timestamp_ms": 1791654010884,
                    "purchased_at_ms": 1791654000000,
                    "expiration_at_ms": 1794332400000,
                    "environment": "SANDBOX",
                    "entitlement_ids": ["premium"],
                    "product_id": "premium_monthly:monthly",
                    "period_type": "NORMAL",
                    "store": "PLAY_STORE",
                    "transaction_id": "GPA.3346-7012-3456-78901",
                    "original_transaction_id": "GPA.3346-7012-3456-78901",
                    "presented_offering_id": null,
                    "subscriber_attributes": {}
                  }
                }
                """.formatted(id, type, appUserId, appUserId, appUserId);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String title(MvcResult result) throws Exception {
        return JsonPath.read(body(result), "$.title");
    }

    private void anaBought() {
        gateway.willAnswer(ana.getId(), FakePaymentGateway.active("GPA.3346-7012-3456-78901"));
    }

    private int subscribeAna() throws Exception {
        anaBought();
        MvcResult created = call(post(SUBSCRIPTIONS), anaToken, "{}");
        assertThat(status(created)).isEqualTo(201);
        return JsonPath.read(body(created), "$.id");
    }

    // ---------- Webhook: authorization ----------

    @Test
    void webhook_withoutAuthorization_is401AndChangesNothing() throws Exception {
        anaBought();

        MvcResult result = webhook(null, event("evt-1", "INITIAL_PURCHASE", String.valueOf(ana.getId())));

        assertThat(status(result)).isEqualTo(401);
        assertThat(title(result)).isEqualTo("InvalidWebhookAuthorization");
        assertThat(gateway.verified()).isEmpty();
        assertThat(queryString("SELECT count(*) FROM processed_webhook_events")).isEqualTo("0");
    }

    @Test
    void webhook_withAWrongAuthorization_is401() throws Exception {
        MvcResult result = webhook("Bearer wrong-secret", event("evt-1", "INITIAL_PURCHASE", "1"));

        assertThat(status(result)).isEqualTo(401);
    }

    @Test
    void webhook_withAUserJwtInsteadOfTheSecret_is401() throws Exception {
        MvcResult result = webhook("Bearer " + anaToken, event("evt-1", "INITIAL_PURCHASE", "1"));

        assertThat(status(result)).isEqualTo(401);
    }

    // ---------- Webhook: events ----------

    @Test
    void webhook_testEvent_isAcknowledgedAndIgnored() throws Exception {
        MvcResult result = webhook(WEBHOOK_AUTHORIZATION, event("evt-test", "TEST", "$RCAnonymousID:test"));

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) JsonPath.read(body(result), "$.outcome")).isEqualTo("Ignored");
        assertThat(gateway.verified()).isEmpty();
        assertThat(queryString("SELECT count(*) FROM processed_webhook_events")).isEqualTo("0");
    }

    @Test
    void webhook_initialPurchase_activatesTheSubscriptionFromTheStateOfRevenueCat() throws Exception {
        anaBought();

        MvcResult result = webhook(WEBHOOK_AUTHORIZATION, event("evt-1", "INITIAL_PURCHASE",
                String.valueOf(ana.getId())));

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) JsonPath.read(body(result), "$.outcome")).isEqualTo("Processed");
        assertThat(queryString("SELECT status FROM subscriptions WHERE student_id = " + ana.getId()))
                .isEqualTo("Active");
        assertThat(queryString("SELECT store_transaction_id FROM subscriptions"))
                .isEqualTo("GPA.3346-7012-3456-78901");
        assertThat(queryString("SELECT event_type FROM processed_webhook_events WHERE event_id = 'evt-1'"))
                .isEqualTo("INITIAL_PURCHASE");
    }

    @Test
    void webhook_sameEventTwice_isAppliedOnce() throws Exception {
        anaBought();
        String json = event("evt-1", "INITIAL_PURCHASE", String.valueOf(ana.getId()));
        webhook(WEBHOOK_AUTHORIZATION, json);

        MvcResult retry = webhook(WEBHOOK_AUTHORIZATION, json);

        assertThat(status(retry)).isEqualTo(200);
        assertThat((String) JsonPath.read(body(retry), "$.outcome")).isEqualTo("Duplicate");
        assertThat(gateway.verified()).hasSize(1);
        assertThat(queryString("SELECT count(*) FROM subscriptions")).isEqualTo("1");
        assertThat(queryString("SELECT count(*) FROM processed_webhook_events")).isEqualTo("1");
    }

    @Test
    void webhook_expiration_sendsTheStudentBackToTheFreePlan() throws Exception {
        subscribeAna();
        gateway.willAnswer(ana.getId(), PurchaseVerification.inactive());

        MvcResult result = webhook(WEBHOOK_AUTHORIZATION, event("evt-2", "EXPIRATION",
                String.valueOf(ana.getId())));

        assertThat(status(result)).isEqualTo(200);
        assertThat(queryString("SELECT status FROM subscriptions")).isEqualTo("Expired");
        MvcResult plan = call(get(SUBSCRIPTIONS + "/" + ana.getId()), anaToken, null);
        assertThat((String) JsonPath.read(body(plan), "$.plan")).isEqualTo("Free");
        assertThat((Object) JsonPath.read(body(plan), "$.subscription")).isNull();
    }

    @Test
    void webhook_expirationThatArrivesLate_doesNotExpireARenewedSubscription() throws Exception {
        subscribeAna();
        gateway.willAnswer(ana.getId(), new PurchaseVerification(true, FakePaymentGateway.PRODUCT,
                Instant.now().plus(Duration.ofDays(60)), true, "GPA.3346-7012-3456-78901..1", false));

        webhook(WEBHOOK_AUTHORIZATION, event("evt-3", "EXPIRATION", String.valueOf(ana.getId())));

        assertThat(queryString("SELECT status FROM subscriptions")).isEqualTo("Active");
        assertThat(queryString("SELECT store_transaction_id FROM subscriptions"))
                .isEqualTo("GPA.3346-7012-3456-78901..1");
    }

    @Test
    void webhook_whenRevenueCatDoesNotAnswer_is503SoItRetries() throws Exception {
        gateway.failWith(new PaymentGatewayException("RevenueCat could not read the customer: timeout."));

        MvcResult result = webhook(WEBHOOK_AUTHORIZATION, event("evt-4", "RENEWAL", String.valueOf(ana.getId())));

        assertThat(status(result)).isEqualTo(503);
        assertThat(title(result)).isEqualTo("PaymentGatewayUnavailable");
        assertThat(queryString("SELECT count(*) FROM processed_webhook_events")).isEqualTo("0");
    }

    @Test
    void webhook_withoutEvent_is400() throws Exception {
        MvcResult result = webhook(WEBHOOK_AUTHORIZATION, "{\"api_version\":\"1.0\"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidWebhookEvent");
    }

    // ---------- Subscriptions ----------

    @Test
    void create_withAVerifiedPurchase_is201WithTheSubscription() throws Exception {
        anaBought();

        MvcResult result = call(post(SUBSCRIPTIONS), anaToken, "{\"productId\":\"premium_monthly\","
                + "\"purchaseToken\":\"ignored\",\"storeTransactionId\":\"forged\"}");

        assertThat(status(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(SUBSCRIPTIONS + "/" + ana.getId());
        String json = body(result);
        assertThat((Integer) JsonPath.read(json, "$.studentId")).isEqualTo(ana.getId());
        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("Active");
        assertThat((String) JsonPath.read(json, "$.planName")).isEqualTo("Plan Mensual");
        assertThat((Double) JsonPath.read(json, "$.price")).isEqualTo(29.90);
        assertThat((String) JsonPath.read(json, "$.currency")).isEqualTo("PEN");
        // The transaction comes from RevenueCat, never from the client.
        assertThat((String) JsonPath.read(json, "$.storeTransactionId")).isEqualTo("GPA.3346-7012-3456-78901");
        assertThat(gateway.verified()).containsExactly(ana.getId());
    }

    @Test
    void create_withoutBody_isAlsoAccepted() throws Exception {
        anaBought();

        assertThat(status(call(post(SUBSCRIPTIONS), anaToken, null))).isEqualTo(201);
    }

    @Test
    void create_withoutAPurchaseInRevenueCat_is422AndActivatesNothing() throws Exception {
        MvcResult result = call(post(SUBSCRIPTIONS), anaToken, "{}");

        assertThat(status(result)).isEqualTo(422);
        assertThat(title(result)).isEqualTo("PurchaseNotVerified");
        assertThat(queryString("SELECT count(*) FROM subscriptions")).isEqualTo("0");
    }

    @Test
    void create_withoutToken_is401() throws Exception {
        assertThat(status(call(post(SUBSCRIPTIONS), null, "{}"))).isEqualTo(401);
    }

    @Test
    void getPlan_withoutSubscription_isTheFreePlanWithItsLimits() throws Exception {
        MvcResult result = call(get(SUBSCRIPTIONS + "/" + ana.getId()), anaToken, null);

        assertThat(status(result)).isEqualTo(200);
        String json = body(result);
        assertThat((String) JsonPath.read(json, "$.plan")).isEqualTo("Free");
        assertThat((Integer) JsonPath.read(json, "$.limits.maxActiveRoutes")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(json, "$.limits.maxTotalRoutes")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(json, "$.limits.monthlyEscalations")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(json, "$.limits.reviewDeadline.amount")).isEqualTo(5);
        assertThat((String) JsonPath.read(json, "$.limits.reviewDeadline.unit")).isEqualTo("BusinessDays");
        assertThat((Object) JsonPath.read(json, "$.subscription")).isNull();
    }

    @Test
    void getPlan_withASubscription_isThePremiumPlanWithItsLimits() throws Exception {
        int id = subscribeAna();

        String json = body(call(get(SUBSCRIPTIONS + "/" + ana.getId()), anaToken, null));

        assertThat((String) JsonPath.read(json, "$.plan")).isEqualTo("Premium");
        assertThat((Integer) JsonPath.read(json, "$.limits.maxActiveRoutes")).isEqualTo(3);
        assertThat((Object) JsonPath.read(json, "$.limits.maxTotalRoutes")).isNull();
        assertThat((Integer) JsonPath.read(json, "$.limits.monthlyEscalations")).isEqualTo(10);
        assertThat((Integer) JsonPath.read(json, "$.limits.reviewDeadline.amount")).isEqualTo(48);
        assertThat((String) JsonPath.read(json, "$.limits.reviewDeadline.unit")).isEqualTo("Hours");
        assertThat((Integer) JsonPath.read(json, "$.subscription.id")).isEqualTo(id);
    }

    @Test
    void getPlan_ofAnotherStudent_is403() throws Exception {
        MvcResult result = call(get(SUBSCRIPTIONS + "/" + ana.getId()), bobToken, null);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotSubscriptionOwner");
    }

    @Test
    void cancel_keepsThePremiumPlanUntilThePeriodEnds() throws Exception {
        int id = subscribeAna();

        MvcResult result = call(patch(SUBSCRIPTIONS + "/" + id + "/cancel"), anaToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) JsonPath.read(body(result), "$.status")).isEqualTo("Cancelled");
        assertThat((String) JsonPath.read(body(result), "$.cancelledAt")).isNotBlank();
        assertThat(gateway.cancelled()).containsExactly("GPA.3346-7012-3456-78901");
        String plan = body(call(get(SUBSCRIPTIONS + "/" + ana.getId()), anaToken, null));
        assertThat((String) JsonPath.read(plan, "$.plan")).isEqualTo("Premium");
    }

    @Test
    void cancel_theSubscriptionOfAnotherStudent_is403() throws Exception {
        int id = subscribeAna();

        assertThat(status(call(patch(SUBSCRIPTIONS + "/" + id + "/cancel"), bobToken, null))).isEqualTo(403);
        assertThat(gateway.cancelled()).isEmpty();
    }

    @Test
    void cancel_anUnknownSubscription_is404() throws Exception {
        assertThat(status(call(patch(SUBSCRIPTIONS + "/999/cancel"), anaToken, null))).isEqualTo(404);
    }
}
