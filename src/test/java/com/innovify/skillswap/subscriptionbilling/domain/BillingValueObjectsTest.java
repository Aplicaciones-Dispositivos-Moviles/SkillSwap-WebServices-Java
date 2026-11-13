package com.innovify.skillswap.subscriptionbilling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.Money;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanType;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BillingValueObjectsTest {

    // ---------- Money ----------

    @Test
    void money_keepsTwoDecimalsAndAnUpperCaseCurrency() {
        Money money = new Money(new BigDecimal("29.9"), " pen ");

        assertThat(money.amount()).isEqualTo(new BigDecimal("29.90"));
        assertThat(money.currency()).isEqualTo("PEN");
        assertThat(Money.soles("29.90")).isEqualTo(money);
    }

    @Test
    void money_acceptsZero() {
        assertThat(Money.soles("0").amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void money_negative_throwsDomainException() {
        assertThatThrownBy(() -> Money.soles("-0.01")).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PE", "SOLES", "P3N"})
    void money_withAnInvalidCurrency_throwsDomainException(String currency) {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, currency)).isInstanceOf(DomainException.class);
    }

    @Test
    void money_withoutAmount_throwsDomainException() {
        assertThatThrownBy(() -> new Money(null, "PEN")).isInstanceOf(DomainException.class);
    }

    // ---------- Plan ----------

    @Test
    void monthlyPlan_costs2990SolesWithVatIncluded() {
        SubscriptionPlan plan = SubscriptionPlan.monthly("premium_monthly");

        assertThat(plan.name()).isEqualTo("Plan Mensual");
        assertThat(plan.price()).isEqualTo(Money.soles("29.90"));
        assertThat(plan.productId()).isEqualTo("premium_monthly");
    }

    @Test
    void plan_withAnInvalidNameProductOrPrice_throwsDomainException() {
        assertThatThrownBy(() -> new SubscriptionPlan(" ", "p", Money.soles("1"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SubscriptionPlan("n", "x".repeat(101), Money.soles("1")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SubscriptionPlan("n", "p", null)).isInstanceOf(DomainException.class);
    }

    // ---------- Limits ----------

    @Test
    void freePlan_allowsOneActiveRouteThreeInTotalAndThreeEscalationsInFiveBusinessDays() {
        PlanLimits free = PlanLimits.of(PlanType.FREE);

        assertThat(free.plan()).isEqualTo(PlanType.FREE);
        assertThat(free.maxActiveRoutes()).isEqualTo(1);
        assertThat(free.maxTotalRoutes()).isEqualTo(3);
        assertThat(free.capsTotalRoutes()).isTrue();
        assertThat(free.monthlyEscalations()).isEqualTo(3);
        assertThat(free.reviewDeadline()).isEqualTo(ReviewDeadline.businessDays(5));
    }

    @Test
    void premiumPlan_allowsThreeActiveRoutesNoTotalCapAndTenEscalationsIn48Hours() {
        PlanLimits premium = PlanLimits.of(PlanType.PREMIUM);

        assertThat(premium.maxActiveRoutes()).isEqualTo(3);
        assertThat(premium.maxTotalRoutes()).isNull();
        assertThat(premium.capsTotalRoutes()).isFalse();
        assertThat(premium.monthlyEscalations()).isEqualTo(10);
        assertThat(premium.reviewDeadline()).isEqualTo(ReviewDeadline.hours(48));
    }

    @Test
    void limits_thatAreNotConsistent_throwDomainException() {
        assertThatThrownBy(() -> new PlanLimits(PlanType.FREE, 0, 3, 3, ReviewDeadline.hours(1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PlanLimits(PlanType.FREE, 2, 1, 3, ReviewDeadline.hours(1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PlanLimits(PlanType.FREE, 1, 3, -1, ReviewDeadline.hours(1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PlanLimits(PlanType.FREE, 1, 3, 3, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PlanLimits(null, 1, 3, 3, ReviewDeadline.hours(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void reviewDeadline_mustBePositiveAndHaveAUnit() {
        assertThatThrownBy(() -> ReviewDeadline.hours(0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ReviewDeadline(1, null)).isInstanceOf(DomainException.class);
        assertThat(ReviewDeadline.businessDays(5).unit().value()).isEqualTo("BusinessDays");
        assertThat(ReviewDeadline.hours(48).unit().value()).isEqualTo("Hours");
    }

    // ---------- Status and verification ----------

    @Test
    void status_roundTripsThroughItsValue() {
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            assertThat(SubscriptionStatus.fromValue(status.value().toUpperCase())).isEqualTo(status);
        }
        assertThatThrownBy(() -> SubscriptionStatus.fromValue("Paused")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verification_activeNeedsAnExpiration() {
        assertThatThrownBy(() -> new PurchaseVerification(true, "p", null, true, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PurchaseVerification.inactive().active()).isFalse();
    }
}
