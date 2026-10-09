package com.innovify.skillswap.subscriptionbilling.infrastructure.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.simulated.SimulatedPaymentGatewayAdapter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayAdapterTest {

    @Test
    void firstVerification_isAnApprovedPurchaseOfOnePeriod() {
        SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(Duration.ofDays(30));
        Instant before = Instant.now();

        PurchaseVerification verification = gateway.verifyPurchase(3, null);

        assertThat(verification.active()).isTrue();
        assertThat(verification.willRenew()).isTrue();
        assertThat(verification.sandbox()).isTrue();
        assertThat(verification.productId()).isEqualTo(SubscriptionPlan.DEFAULT_MONTHLY_PRODUCT_ID);
        assertThat(verification.storeTransactionId()).startsWith(SimulatedPaymentGatewayAdapter.TRANSACTION_PREFIX);
        assertThat(verification.expiresAt()).isBetween(before.plus(Duration.ofDays(30)),
                Instant.now().plus(Duration.ofDays(30)));
    }

    @Test
    void laterVerifications_returnTheSameSubscription() {
        SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(Duration.ofDays(30));

        PurchaseVerification first = gateway.verifyPurchase(3, "premium_monthly");
        PurchaseVerification second = gateway.verifyPurchase(3, null);

        assertThat(second).isEqualTo(first);
        assertThat(gateway.verifyPurchase(4, null).storeTransactionId()).isNotEqualTo(first.storeTransactionId());
    }

    @Test
    void anEndedPeriod_isRenewedUntilItIsCancelled() throws Exception {
        SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(Duration.ofMillis(30));
        PurchaseVerification first = gateway.verifyPurchase(3, null);
        Thread.sleep(80);

        PurchaseVerification renewed = gateway.verifyPurchase(3, null);

        assertThat(renewed.active()).isTrue();
        assertThat(renewed.expiresAt()).isAfter(first.expiresAt());
        assertThat(renewed.storeTransactionId()).isEqualTo(first.storeTransactionId());
    }

    @Test
    void aCancelledSubscription_keepsThePeriodAndThenEnds() throws Exception {
        SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(Duration.ofMillis(60));
        PurchaseVerification first = gateway.verifyPurchase(3, null);

        gateway.cancelRenewal(3, first.storeTransactionId());
        PurchaseVerification cancelled = gateway.verifyPurchase(3, null);
        assertThat(cancelled.active()).isTrue();
        assertThat(cancelled.willRenew()).isFalse();

        Thread.sleep(120);
        assertThat(gateway.verifyPurchase(3, null).active()).isFalse();
        // A new purchase after that starts a new subscription.
        assertThat(gateway.verifyPurchase(3, null).storeTransactionId())
                .isNotEqualTo(first.storeTransactionId());
    }

    @Test
    void cancelRenewal_ofAnUnknownStudent_doesNothing() {
        SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(Duration.ofDays(30));

        gateway.cancelRenewal(3, null);

        assertThat(gateway.verifyPurchase(3, null).willRenew()).isTrue();
    }

    @Test
    void aPeriodThatIsNotPositive_isRejected() {
        assertThatThrownBy(() -> new SimulatedPaymentGatewayAdapter(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
