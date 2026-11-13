package com.innovify.skillswap.subscriptionbilling.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.subscriptionbilling.TestData;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacadeImpl;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeSubscriptionRepository;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetCurrentSubscriptionByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetPlanLimitsByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PlanLimits;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubscriptionQueryServiceImplTest {

    private final FakeSubscriptionRepository subscriptions = new FakeSubscriptionRepository();
    private final SubscriptionQueryServiceImpl service = new SubscriptionQueryServiceImpl(subscriptions);
    private final SubscriptionContextFacadeImpl facade = new SubscriptionContextFacadeImpl(service);

    private PlanLimits limitsOf(int studentId) {
        return service.handle(new GetPlanLimitsByStudentIdQuery(studentId));
    }

    @Test
    void aStudentWithoutSubscription_isOnTheFreePlanWithoutAnyRow() {
        assertThat(limitsOf(3)).isEqualTo(PlanLimits.FREE);
        assertThat(service.handle(new GetCurrentSubscriptionByStudentIdQuery(3))).isEmpty();
    }

    @Test
    void anActiveSubscription_givesThePremiumLimits() {
        subscriptions.save(TestData.activeSubscription(3));

        assertThat(limitsOf(3)).isEqualTo(PlanLimits.PREMIUM);
        assertThat(limitsOf(4)).isEqualTo(PlanLimits.FREE);
    }

    @Test
    void aCancelledSubscription_keepsThePremiumLimitsUntilThePeriodEnds() {
        subscriptions.save(TestData.activeSubscription(3).cancel());

        assertThat(limitsOf(3)).isEqualTo(PlanLimits.PREMIUM);
    }

    @Test
    void anEndedPeriod_givesTheFreeLimitsEvenBeforeItIsMarkedExpired() {
        Subscription subscription = subscriptions.save(TestData.activeSubscription(3));
        TestData.withPeriodEnd(subscription, Instant.now().minusSeconds(1));

        assertThat(limitsOf(3)).isEqualTo(PlanLimits.FREE);
        assertThat(service.handle(new GetCurrentSubscriptionByStudentIdQuery(3))).contains(subscription);
    }

    @Test
    void anExpiredSubscription_givesTheFreeLimitsAndIsNotCurrent() {
        subscriptions.save(TestData.activeSubscription(3).expire());

        assertThat(limitsOf(3)).isEqualTo(PlanLimits.FREE);
        assertThat(service.handle(new GetCurrentSubscriptionByStudentIdQuery(3))).isEmpty();
    }

    @Test
    void facade_exposesTheFreeLimitsWithTheDeadlineInBusinessDays() {
        PlanLimitsView view = facade.getPlanLimits(3);

        assertThat(view).isEqualTo(new PlanLimitsView("Free", 1, 3, 3, null, 5));
        assertThat(view.isFree()).isTrue();
    }

    @Test
    void facade_exposesThePremiumLimitsWithTheDeadlineInHours() {
        subscriptions.save(TestData.activeSubscription(3));

        PlanLimitsView view = facade.getPlanLimits(3);

        assertThat(view).isEqualTo(new PlanLimitsView("Premium", 3, null, 10, 48, null));
        assertThat(view.isFree()).isFalse();
    }
}
