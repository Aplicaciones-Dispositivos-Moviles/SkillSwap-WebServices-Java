package com.innovify.skillswap.subscriptionbilling.infrastructure.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatWebhookAuthorization;
import org.junit.jupiter.api.Test;

class RevenueCatWebhookAuthorizationTest {

    private final RevenueCatWebhookAuthorization authorization =
            new RevenueCatWebhookAuthorization("Bearer whsec_7f3a9c");

    @Test
    void theConfiguredValue_isAuthorized() {
        assertThat(authorization.isAuthorized("Bearer whsec_7f3a9c")).isTrue();
        assertThat(authorization.isAuthorized("  Bearer whsec_7f3a9c ")).isTrue();
    }

    @Test
    void aMissingOrWrongValue_isRejected() {
        assertThat(authorization.isAuthorized(null)).isFalse();
        assertThat(authorization.isAuthorized("")).isFalse();
        assertThat(authorization.isAuthorized("Bearer whsec_7f3a9")).isFalse();
        assertThat(authorization.isAuthorized("Bearer whsec_7f3a9c0")).isFalse();
        assertThat(authorization.isAuthorized("whsec_7f3a9c")).isFalse();
    }

    @Test
    void withoutAConfiguredSecret_everythingIsRejected() {
        RevenueCatWebhookAuthorization notConfigured = new RevenueCatWebhookAuthorization(" ");

        assertThat(notConfigured.isAuthorized(" ")).isFalse();
        assertThat(notConfigured.isAuthorized("anything")).isFalse();
        assertThat(new RevenueCatWebhookAuthorization(null).isAuthorized("anything")).isFalse();
    }
}
