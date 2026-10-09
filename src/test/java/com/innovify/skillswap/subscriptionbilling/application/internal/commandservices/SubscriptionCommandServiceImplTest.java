package com.innovify.skillswap.subscriptionbilling.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.TestData;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.WebhookEventOutcome;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakePaymentGateway;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeProcessedWebhookEventRepository;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakeSubscriptionRepository;
import com.innovify.skillswap.subscriptionbilling.application.fakes.TestMessages;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CancelSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ExpireSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionActivated;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionExpired;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class SubscriptionCommandServiceImplTest {

    private static final int STUDENT = 3;

    private final FakeSubscriptionRepository subscriptions = new FakeSubscriptionRepository();
    private final FakeProcessedWebhookEventRepository processedEvents = new FakeProcessedWebhookEventRepository();
    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final FakeDomainEventPublisher publisher = new FakeDomainEventPublisher();
    private final AtomicInteger transactions = new AtomicInteger();
    private SubscriptionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = newService(true);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private SubscriptionCommandServiceImpl newService(boolean acceptSandboxEvents) {
        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };
        return new SubscriptionCommandServiceImpl(subscriptions, processedEvents, gateway, publisher, counting,
                acceptSandboxEvents, TestMessages.source());
    }

    private static PurchaseVerification activeUntil(Instant end, boolean willRenew, String transaction) {
        return new PurchaseVerification(true, TestData.PRODUCT, end, willRenew, transaction, false);
    }

    private static ProcessRevenueCatEventCommand event(String id, String type, String appUserId) {
        return new ProcessRevenueCatEventCommand(id, type, appUserId, appUserId, List.of(appUserId), "PRODUCTION");
    }

    private Result<WebhookEventOutcome> notify(String id, String type) {
        return service.handle(event(id, type, String.valueOf(STUDENT)));
    }

    private Subscription seedActive() {
        return subscriptions.save(TestData.activeSubscription(STUDENT));
    }

    private static void assertFailure(Result<?> result, SubscriptionBillingError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank();
    }

    // ---------- Create (after a purchase in the app) ----------

    @Test
    void create_withAVerifiedPurchase_activatesTheSubscriptionWithThePeriodOfTheGateway() {
        Instant end = Instant.now().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(end, true, "GPA.1"));

        Result<Subscription> result = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(result.isSuccess()).isTrue();
        Subscription subscription = result.value();
        assertThat(subscription.getId()).isNotNull();
        assertThat(subscription.getStudentId()).isEqualTo(STUDENT);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(end);
        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1");
        assertThat(subscription.getPlan().productId()).isEqualTo(TestData.PRODUCT);
        assertThat(gateway.verified()).containsExactly(STUDENT);
        assertThat(publisher.published()).containsExactly(
                new SubscriptionActivated(subscription.getId(), STUDENT, end));
        assertThat(subscriptions.lockedReads()).isEqualTo(1);
        assertThat(transactions.get()).isEqualTo(1);
    }

    @Test
    void create_withAPurchaseThatWillNotRenew_isActivatedAsCancelled() {
        gateway.willAnswer(STUDENT, activeUntil(Instant.now().plus(Duration.ofDays(3)), false, "GPA.1"));

        Result<Subscription> result = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(result.value().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void create_withoutAnActivePurchase_returnsPurchaseNotVerifiedAndActivatesNothing() {
        Result<Subscription> result = service.handle(new CreateSubscriptionCommand(STUDENT, "premium_monthly"));

        assertFailure(result, SubscriptionBillingError.PURCHASE_NOT_VERIFIED);
        assertThat(subscriptions.items()).isEmpty();
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void create_whenTheGatewayFails_returnsPaymentGatewayUnavailable() {
        gateway.failWith(new PaymentGatewayException("RevenueCat could not read the customer: it answered 500."));

        assertFailure(service.handle(new CreateSubscriptionCommand(STUDENT, null)),
                SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
        assertThat(subscriptions.items()).isEmpty();
    }

    @Test
    void create_withABlankOrTooLongProduct_returnsInvalidProductWithoutCallingTheGateway() {
        assertFailure(service.handle(new CreateSubscriptionCommand(STUDENT, " ")),
                SubscriptionBillingError.INVALID_PRODUCT);
        assertFailure(service.handle(new CreateSubscriptionCommand(STUDENT, "x".repeat(101))),
                SubscriptionBillingError.INVALID_PRODUCT);
        assertThat(gateway.verified()).isEmpty();
    }

    @Test
    void create_whenTheWebhookAlreadyActivatedIt_returnsTheSameSubscriptionUpToDate() {
        Subscription existing = seedActive();
        Instant later = existing.getCurrentPeriodEnd().plus(Duration.ofDays(1));
        gateway.willAnswer(STUDENT, activeUntil(later, true, "GPA.2"));

        Result<Subscription> result = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(result.value()).isSameAs(existing);
        assertThat(existing.getCurrentPeriodEnd()).isEqualTo(later);
        assertThat(subscriptions.items()).hasSize(1);
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void create_whenAnotherRequestCreatesItAtTheSameTime_appliesTheStateToThatOne() {
        Instant end = Instant.now().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(end, true, "GPA.1"));
        subscriptions.beforeNextInsert(() -> subscriptions.save(TestData.activeSubscription(STUDENT)));

        Result<Subscription> result = service.handle(new CreateSubscriptionCommand(STUDENT, null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(subscriptions.items()).hasSize(1);
        assertThat(result.value()).isSameAs(subscriptions.items().get(0));
    }

    @Test
    void create_whenTheDatabaseFails_returnsDatabaseError() {
        gateway.willAnswer(STUDENT, FakePaymentGateway.active("GPA.1"));
        subscriptions.failOnSave(new DataAccessResourceFailureException("down"));

        assertFailure(service.handle(new CreateSubscriptionCommand(STUDENT, null)),
                SubscriptionBillingError.DATABASE_ERROR);
        assertThat(publisher.published()).isEmpty();
    }

    // ---------- Cancel ----------

    @Test
    void cancel_stopsTheRenewalsInTheStoreAndKeepsThePlanUntilThePeriodEnds() {
        Subscription subscription = seedActive();
        Instant end = subscription.getCurrentPeriodEnd();

        Result<Subscription> result = service.handle(new CancelSubscriptionCommand(subscription.getId(), STUDENT));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.value().getCurrentPeriodEnd()).isEqualTo(end);
        assertThat(result.value().grantsPremiumAt(Instant.now())).isTrue();
        assertThat(gateway.cancelled()).containsExactly("GPA.1234-5678-9012-34567");
    }

    @Test
    void cancel_twice_changesNothingAndDoesNotCallTheStoreAgain() {
        Subscription subscription = seedActive();
        service.handle(new CancelSubscriptionCommand(subscription.getId(), STUDENT));

        Result<Subscription> again = service.handle(new CancelSubscriptionCommand(subscription.getId(), STUDENT));

        assertThat(again.isSuccess()).isTrue();
        assertThat(again.value().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(gateway.cancelled()).hasSize(1);
    }

    @Test
    void cancel_aSubscriptionOfAnotherStudent_returnsNotSubscriptionOwner() {
        Subscription subscription = seedActive();

        assertFailure(service.handle(new CancelSubscriptionCommand(subscription.getId(), 99)),
                SubscriptionBillingError.NOT_SUBSCRIPTION_OWNER);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(gateway.cancelled()).isEmpty();
    }

    @Test
    void cancel_anUnknownSubscription_returnsSubscriptionNotFound() {
        assertFailure(service.handle(new CancelSubscriptionCommand(42, STUDENT)),
                SubscriptionBillingError.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void cancel_anExpiredSubscription_returnsSubscriptionNotActive() {
        Subscription subscription = seedActive();
        subscription.expire();

        assertFailure(service.handle(new CancelSubscriptionCommand(subscription.getId(), STUDENT)),
                SubscriptionBillingError.SUBSCRIPTION_NOT_ACTIVE);
    }

    @Test
    void cancel_whenTheStoreFails_returnsPaymentGatewayUnavailableAndKeepsItActive() {
        Subscription subscription = seedActive();
        gateway.failWith(new PaymentGatewayException("timeout"));

        assertFailure(service.handle(new CancelSubscriptionCommand(subscription.getId(), STUDENT)),
                SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // ---------- Expire (periodic check) ----------

    @Test
    void expire_whenTheGatewayReportsNoRenewal_expiresAndPublishesSubscriptionExpired() {
        Subscription subscription = TestData.withPeriodEnd(seedActive(), Instant.now().minusSeconds(60));

        Result<Subscription> result = service.handle(new ExpireSubscriptionCommand(subscription.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(publisher.published()).containsExactly(
                new SubscriptionExpired(subscription.getId(), STUDENT, subscription.getExpiredAt()));
    }

    @Test
    void expire_whenTheGatewayReportsARenewal_extendsThePeriodInstead() {
        Subscription subscription = TestData.withPeriodEnd(seedActive(), Instant.now().minusSeconds(60));
        Instant renewed = Instant.now().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(renewed, true, "GPA.1..1"));

        service.handle(new ExpireSubscriptionCommand(subscription.getId()));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(renewed);
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void expire_aSubscriptionWhosePeriodHasNotEnded_changesNothing() {
        Subscription subscription = seedActive();

        Result<Subscription> result = service.handle(new ExpireSubscriptionCommand(subscription.getId()));

        assertThat(result.value().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(gateway.verified()).isEmpty();
    }

    @Test
    void expire_whenTheGatewayFails_keepsItAndReturnsPaymentGatewayUnavailable() {
        Subscription subscription = TestData.withPeriodEnd(seedActive(), Instant.now().minusSeconds(60));
        gateway.failWith(new PaymentGatewayException("timeout"));

        assertFailure(service.handle(new ExpireSubscriptionCommand(subscription.getId())),
                SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
        assertThat(subscription.isExpired()).isFalse();
    }

    @Test
    void expire_anUnknownSubscription_returnsSubscriptionNotFound() {
        assertFailure(service.handle(new ExpireSubscriptionCommand(42)),
                SubscriptionBillingError.SUBSCRIPTION_NOT_FOUND);
    }

    // ---------- Webhook ----------

    @Test
    void webhook_testEvent_isIgnoredWithoutCallingTheGatewayOrRecordingIt() {
        Result<WebhookEventOutcome> result = notify("evt-test", "TEST");

        assertThat(result.value()).isEqualTo(WebhookEventOutcome.IGNORED);
        assertThat(gateway.verified()).isEmpty();
        assertThat(processedEvents.items()).isEmpty();
    }

    @Test
    void webhook_initialPurchase_readsTheStateFromTheGatewayAndActivates() {
        Instant end = Instant.now().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(end, true, "GPA.1"));

        Result<WebhookEventOutcome> result = notify("evt-1", "INITIAL_PURCHASE");

        assertThat(result.value()).isEqualTo(WebhookEventOutcome.PROCESSED);
        assertThat(subscriptions.findCurrentByStudentId(STUDENT).orElseThrow().getCurrentPeriodEnd()).isEqualTo(end);
        assertThat(processedEvents.items()).extracting(e -> e.getEventId()).containsExactly("evt-1");
        assertThat(processedEvents.items().get(0).getEventType()).isEqualTo("INITIAL_PURCHASE");
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    void webhook_sameEventTwice_isAppliedOnlyOnce() {
        gateway.willAnswer(STUDENT, FakePaymentGateway.active("GPA.1"));
        notify("evt-1", "INITIAL_PURCHASE");

        Result<WebhookEventOutcome> again = notify("evt-1", "INITIAL_PURCHASE");

        assertThat(again.value()).isEqualTo(WebhookEventOutcome.DUPLICATE);
        assertThat(gateway.verified()).hasSize(1);
        assertThat(processedEvents.items()).hasSize(1);
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    void webhook_renewal_extendsThePeriod() {
        Subscription subscription = seedActive();
        Instant renewed = subscription.getCurrentPeriodEnd().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(renewed, true, "GPA.1..1"));

        notify("evt-2", "RENEWAL");

        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(renewed);
        assertThat(subscription.getStoreTransactionId()).isEqualTo("GPA.1..1");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void webhook_cancellation_cancelsAndUncancellationResumes() {
        Subscription subscription = seedActive();
        Instant end = subscription.getCurrentPeriodEnd();
        gateway.willAnswer(STUDENT, activeUntil(end, false, null));

        notify("evt-3", "CANCELLATION");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(end);

        gateway.willAnswer(STUDENT, activeUntil(end, true, null));
        notify("evt-4", "UNCANCELLATION");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void webhook_expiration_expiresTheSubscriptionAndPublishesSubscriptionExpired() {
        Subscription subscription = seedActive();

        Result<WebhookEventOutcome> result = notify("evt-5", "EXPIRATION");

        assertThat(result.value()).isEqualTo(WebhookEventOutcome.PROCESSED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(publisher.published()).containsExactly(
                new SubscriptionExpired(subscription.getId(), STUDENT, subscription.getExpiredAt()));
    }

    @Test
    void webhook_theTypeDoesNotMatter_theStateOfTheGatewayWins() {
        Subscription subscription = seedActive();
        Instant renewed = subscription.getCurrentPeriodEnd().plus(Duration.ofDays(30));
        gateway.willAnswer(STUDENT, activeUntil(renewed, true, null));

        // A late EXPIRATION after the student bought again does not expire the new period.
        notify("evt-6", "EXPIRATION");

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(renewed);
    }

    @Test
    void webhook_ofAnAnonymousUser_isRecordedAndIgnored() {
        Result<WebhookEventOutcome> result = service.handle(
                event("evt-7", "INITIAL_PURCHASE", "$RCAnonymousID:8bd8b4f5d2b649d8a0fa"));

        assertThat(result.value()).isEqualTo(WebhookEventOutcome.IGNORED);
        assertThat(gateway.verified()).isEmpty();
        assertThat(processedEvents.items()).hasSize(1);
    }

    @Test
    void webhook_findsTheStudentAmongTheAliases() {
        gateway.willAnswer(STUDENT, FakePaymentGateway.active("GPA.1"));

        service.handle(new ProcessRevenueCatEventCommand("evt-8", "INITIAL_PURCHASE", "$RCAnonymousID:abc", null,
                List.of("$RCAnonymousID:abc", "3"), "PRODUCTION"));

        assertThat(gateway.verified()).containsExactly(STUDENT);
    }

    @Test
    void webhook_sandboxEvents_canBeIgnored() {
        service = newService(false);
        gateway.willAnswer(STUDENT, FakePaymentGateway.active("GPA.1"));

        Result<WebhookEventOutcome> result = service.handle(new ProcessRevenueCatEventCommand("evt-9",
                "INITIAL_PURCHASE", "3", null, null, "SANDBOX"));

        assertThat(result.value()).isEqualTo(WebhookEventOutcome.IGNORED);
        assertThat(subscriptions.items()).isEmpty();
    }

    @Test
    void webhook_whenTheGatewayFails_returnsPaymentGatewayUnavailableAndDoesNotRecordIt() {
        gateway.failWith(new PaymentGatewayException("timeout"));

        assertFailure(notify("evt-10", "RENEWAL"), SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
        assertThat(processedEvents.items()).isEmpty();

        // RevenueCat retries the same event later, and then it is applied.
        gateway.failWith(null);
        gateway.willAnswer(STUDENT, FakePaymentGateway.active("GPA.1"));
        assertThat(notify("evt-10", "RENEWAL").value()).isEqualTo(WebhookEventOutcome.PROCESSED);
    }

    @Test
    void webhook_withoutIdOrType_returnsInvalidWebhookEvent() {
        assertFailure(notify(" ", "RENEWAL"), SubscriptionBillingError.INVALID_WEBHOOK_EVENT);
        assertFailure(notify("evt-11", null), SubscriptionBillingError.INVALID_WEBHOOK_EVENT);
        assertFailure(notify("x".repeat(256), "RENEWAL"), SubscriptionBillingError.INVALID_WEBHOOK_EVENT);
    }

    @Test
    void resolveStudent_onlyAcceptsPositiveIntegerIds() {
        assertThat(SubscriptionCommandServiceImpl.resolveStudent(event("e", "RENEWAL", "42")).getAsInt()).isEqualTo(42);
        assertThat(SubscriptionCommandServiceImpl.resolveStudent(event("e", "RENEWAL", "0"))).isEmpty();
        assertThat(SubscriptionCommandServiceImpl.resolveStudent(event("e", "RENEWAL", "-4"))).isEmpty();
        assertThat(SubscriptionCommandServiceImpl.resolveStudent(event("e", "RENEWAL", "99999999999"))).isEmpty();
        assertThat(SubscriptionCommandServiceImpl.resolveStudent(event("e", "RENEWAL", "ana"))).isEmpty();
    }
}
