package com.innovify.skillswap.subscriptionbilling.application.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeProcessedWebhookEventRepository;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeSubscriptionRepository;
import com.innovify.skillswap.subscriptionbilling.application.fakes.TestMessages;
import com.innovify.skillswap.subscriptionbilling.application.internal.commandservices.SubscriptionCommandServiceImpl;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CancelSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ExpireSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.simulated.SimulatedPaymentGatewayAdapter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Smoke test of the subscription life cycle exactly as it runs in Render while REVENUECAT_API_KEY is empty: the real
 * {@link SimulatedPaymentGatewayAdapter} behind the real command service (buy, cancel, expire), with in-memory
 * repositories. It proves the flow the app can demo without a RevenueCat account or Google Play Console.
 */
class SimulatedSubscriptionSmokeTest {

    private static final int STUDENT = 7;
    private static final Duration PERIOD = Duration.ofMillis(250);

    private final FakeSubscriptionRepository subscriptions = new FakeSubscriptionRepository();
    private final SimulatedPaymentGatewayAdapter gateway = new SimulatedPaymentGatewayAdapter(PERIOD);
    private final SubscriptionCommandServiceImpl service = new SubscriptionCommandServiceImpl(subscriptions,
            new FakeProcessedWebhookEventRepository(), gateway, new FakeDomainEventPublisher(),
            TransactionOperations.withoutTransaction(), true, TestMessages.source());

    @Test
    void buyingGivesPremiumAndCancellingKeepsItUntilThePeriodEndsThenTheStudentIsBackOnTheFreePlan()
            throws InterruptedException {
        Result<Subscription> bought = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(bought.isSuccess()).isTrue();
        Subscription subscription = bought.value();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getStoreTransactionId()).startsWith(SimulatedPaymentGatewayAdapter.TRANSACTION_PREFIX);
        assertThat(subscription.grantsPremiumAt(Instant.now())).isTrue();

        Result<Subscription> cancelled = service.handle(
                new CancelSubscriptionCommand(subscription.getId(), STUDENT));

        assertThat(cancelled.isSuccess()).isTrue();
        assertThat(cancelled.value().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.value().grantsPremiumAt(Instant.now())).as("premium until the period ends").isTrue();

        Thread.sleep(PERIOD.toMillis() + 150);
        Result<Subscription> expired = service.handle(new ExpireSubscriptionCommand(subscription.getId()));

        assertThat(expired.isSuccess()).isTrue();
        assertThat(expired.value().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.value().grantsPremiumAt(Instant.now())).isFalse();
    }

    @Test
    void aSubscriptionThatIsNotCancelledRenewsWhenItsPeriodEnds() throws InterruptedException {
        Subscription subscription = service.handle(new CreateSubscriptionCommand(STUDENT, null)).value();
        Instant firstEnd = subscription.getCurrentPeriodEnd();

        Thread.sleep(PERIOD.toMillis() + 150);
        Result<Subscription> checked = service.handle(new ExpireSubscriptionCommand(subscription.getId()));

        assertThat(checked.isSuccess()).isTrue();
        assertThat(checked.value().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(checked.value().getCurrentPeriodEnd()).isAfter(firstEnd);
        assertThat(checked.value().grantsPremiumAt(Instant.now())).isTrue();
    }

    @Test
    void aStudentCanSubscribeAgainAfterTheCancelledSubscriptionExpired() throws InterruptedException {
        Subscription first = service.handle(new CreateSubscriptionCommand(STUDENT, null)).value();
        service.handle(new CancelSubscriptionCommand(first.getId(), STUDENT));
        Thread.sleep(PERIOD.toMillis() + 150);
        service.handle(new ExpireSubscriptionCommand(first.getId()));

        Result<Subscription> again = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(again.isSuccess()).isTrue();
        assertThat(again.value().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(again.value().getId()).isNotEqualTo(first.getId());
    }
}
