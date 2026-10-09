package com.innovify.skillswap.subscriptionbilling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import com.innovify.skillswap.subscriptionbilling.TestData;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubscriptionTest {

    private static final Instant IN_30_DAYS = Instant.now().plus(Duration.ofDays(30));

    private static Subscription active() {
        return TestData.activeSubscription(3);
    }

    // ---------- Activation ----------

    @Test
    void constructor_activatesTheSubscriptionWithThePlanAndPeriod() {
        Subscription subscription = new Subscription(3, SubscriptionPlan.monthly("premium_monthly"), " GPA.1 ",
                IN_30_DAYS);

        assertThat(subscription.getStudentId()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(IN_30_DAYS);
        assertThat(subscription.getStartedAt()).isNotNull();
        assertThat(subscription.getCancelledAt()).isNull();
        assertThat(subscription.getExpiredAt()).isNull();
        assertThat(subscription.getPlan().name()).isEqualTo("Plan Mensual");
        assertThat(subscription.getPlan().productId()).isEqualTo("premium_monthly");
        assertThat(subscription.getPlan().price().amount()).isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(subscription.getPlan().price().currency()).isEqualTo("PEN");
    }

    @Test
    void constructor_withoutTransaction_keepsItNull() {
        Subscription subscription = new Subscription(3, SubscriptionPlan.monthly(null), "  ", IN_30_DAYS);

        assertThat(subscription.getStoreTransactionId()).isNull();
        assertThat(subscription.getPlan().productId()).isEqualTo(SubscriptionPlan.DEFAULT_MONTHLY_PRODUCT_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withAnInvalidStudent_throwsDomainException(int studentId) {
        assertThatThrownBy(() -> new Subscription(studentId, SubscriptionPlan.monthly(null), null, IN_30_DAYS))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withoutPlan_throwsDomainException() {
        assertThatThrownBy(() -> new Subscription(3, null, null, IN_30_DAYS)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAPeriodThatAlreadyEnded_throwsDomainException() {
        Instant past = Instant.now().minusSeconds(1);

        assertThatThrownBy(() -> new Subscription(3, SubscriptionPlan.monthly(null), null, past))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Subscription(3, SubscriptionPlan.monthly(null), null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withATooLongTransaction_throwsDomainException() {
        String transaction = "x".repeat(Subscription.MAX_STORE_TRANSACTION_ID_LENGTH + 1);

        assertThatThrownBy(() -> new Subscription(3, SubscriptionPlan.monthly(null), transaction, IN_30_DAYS))
                .isInstanceOf(DomainException.class);
    }

    // ---------- Premium ----------

    @Test
    void grantsPremium_whileThePaidPeriodLasts() {
        Subscription subscription = active();

        assertThat(subscription.grantsPremiumAt(Instant.now())).isTrue();
        assertThat(subscription.grantsPremiumAt(subscription.getCurrentPeriodEnd())).isFalse();
    }

    @Test
    void grantsPremium_aCancelledSubscriptionUntilTheEndOfThePeriod() {
        Subscription subscription = active().cancel();

        assertThat(subscription.grantsPremiumAt(Instant.now())).isTrue();
    }

    @Test
    void grantsPremium_notAfterThePeriodEndedEvenIfItIsNotExpiredYet() {
        Subscription subscription = TestData.withPeriodEnd(active(), Instant.now().minusSeconds(60));

        assertThat(subscription.isExpired()).isFalse();
        assertThat(subscription.grantsPremiumAt(Instant.now())).isFalse();
    }

    @Test
    void grantsPremium_neverOnceExpired() {
        Subscription subscription = active().expire();

        assertThat(subscription.grantsPremiumAt(Instant.now())).isFalse();
    }

    // ---------- Renewal ----------

    @Test
    void renew_extendsThePeriodAndUpdatesTheTransaction() {
        Subscription subscription = active();
        Instant later = subscription.getCurrentPeriodEnd().plus(Duration.ofDays(30));

        boolean renewed = subscription.renew(later, "GPA.1234-5678-9012-34567..0");

        assertThat(renewed).isTrue();
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(later);
        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1234-5678-9012-34567..0");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void renew_neverShortensThePeriod() {
        Subscription subscription = active();
        Instant end = subscription.getCurrentPeriodEnd();

        assertThat(subscription.renew(end, "other")).isFalse();
        assertThat(subscription.renew(end.minusSeconds(10), "other")).isFalse();
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(end);
        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1234-5678-9012-34567");
    }

    @Test
    void renew_withoutTransaction_keepsThePreviousOne() {
        Subscription subscription = active();

        subscription.renew(subscription.getCurrentPeriodEnd().plusSeconds(60), null);

        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1234-5678-9012-34567");
    }

    @Test
    void renew_anExpiredSubscription_throwsDomainException() {
        Subscription subscription = active().expire();

        assertThatThrownBy(() -> subscription.renew(IN_30_DAYS.plusSeconds(60), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void renew_withoutEnd_throwsDomainException() {
        assertThatThrownBy(() -> active().renew(null, null)).isInstanceOf(DomainException.class);
    }

    // ---------- Cancellation ----------

    @Test
    void cancel_keepsThePeriodAndRecordsWhen() {
        Subscription subscription = active();
        Instant end = subscription.getCurrentPeriodEnd();

        subscription.cancel();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscription.isCancelled()).isTrue();
        assertThat(subscription.getCancelledAt()).isNotNull();
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(end);
    }

    @Test
    void cancel_twice_throwsDomainException() {
        Subscription subscription = active().cancel();

        assertThatThrownBy(subscription::cancel).isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_anExpiredSubscription_throwsDomainException() {
        Subscription subscription = active().expire();

        assertThatThrownBy(subscription::cancel).isInstanceOf(DomainException.class);
    }

    @Test
    void uncancel_makesItActiveAgain() {
        Subscription subscription = active().cancel().uncancel();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCancelledAt()).isNull();
    }

    @Test
    void uncancel_anActiveSubscription_throwsDomainException() {
        assertThatThrownBy(() -> active().uncancel()).isInstanceOf(DomainException.class);
    }

    // ---------- Expiration ----------

    @Test
    void expire_fromActive_recordsWhen() {
        Subscription subscription = active().expire();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.isExpired()).isTrue();
        assertThat(subscription.getExpiredAt()).isNotNull();
    }

    @Test
    void expire_fromCancelled_isAllowed() {
        Subscription subscription = active().cancel().expire();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.getCancelledAt()).isNotNull();
    }

    @Test
    void expire_twice_throwsDomainException() {
        Subscription subscription = active().expire();

        assertThatThrownBy(subscription::expire).isInstanceOf(DomainException.class);
    }

    @Test
    void isOwnedBy_onlyItsStudent() {
        assertThat(active().isOwnedBy(3)).isTrue();
        assertThat(active().isOwnedBy(4)).isFalse();
    }
}
