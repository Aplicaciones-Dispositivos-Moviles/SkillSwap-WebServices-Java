package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.innovify.skillswap.subscriptionbilling.TestData;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.ProcessedWebhookEventRepository;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SubscriptionBillingPersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private ProcessedWebhookEventRepository processedEvents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ---------- Subscriptions ----------

    @Test
    void subscription_isStoredWithItsFlattenedPlanAndPeriod() throws Exception {
        Subscription saved = subscriptions.save(TestData.activeSubscription(3));

        Subscription loaded = subscriptions.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getStudentId()).isEqualTo(3);
        assertThat(loaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(loaded.getPlan().name()).isEqualTo("Plan Mensual");
        assertThat(loaded.getPlan().productId()).isEqualTo(TestData.PRODUCT);
        assertThat(loaded.getPlan().price().amount()).isEqualTo(new BigDecimal("29.90"));
        assertThat(loaded.getPlan().price().currency()).isEqualTo("PEN");
        assertThat(loaded.getStoreTransactionId()).isEqualTo("GPA.1234-5678-9012-34567");
        // PostgreSQL keeps microseconds.
        assertThat(loaded.getCurrentPeriodEnd()).isCloseTo(saved.getCurrentPeriodEnd(), within(1, ChronoUnit.MILLIS));
        assertThat(queryString("SELECT status FROM subscriptions")).isEqualTo("Active");
        assertThat(queryString("SELECT plan_price FROM subscriptions")).isEqualTo("29.90");
    }

    @Test
    void subscription_changesAreStoredInPlace() throws Exception {
        Subscription saved = subscriptions.save(TestData.activeSubscription(3));

        subscriptions.save(subscriptions.findById(saved.getId()).orElseThrow().cancel());

        assertThat(queryString("SELECT count(*) FROM subscriptions")).isEqualTo("1");
        Subscription loaded = subscriptions.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(loaded.getCancelledAt()).isNotNull();
    }

    @Test
    void currentSubscription_isTheOneThatHasNotExpired() {
        Subscription old = subscriptions.save(TestData.activeSubscription(3));
        subscriptions.save(old.expire());
        Subscription current = subscriptions.save(TestData.activeSubscription(3));

        assertThat(subscriptions.findCurrentByStudentId(3)).map(Subscription::getId).contains(current.getId());
        assertThat(subscriptions.findCurrentByStudentId(4)).isEmpty();
    }

    @Test
    void currentSubscription_canBeReadWithALockInsideATransaction() {
        subscriptions.save(TestData.activeSubscription(3));

        Integer studentId = new TransactionTemplate(transactionManager).execute(status ->
                subscriptions.findCurrentByStudentIdForUpdate(3).orElseThrow().getStudentId());

        assertThat(studentId).isEqualTo(3);
    }

    @Test
    void aStudent_cannotHaveTwoSubscriptionsThatHaveNotExpired() {
        subscriptions.save(TestData.activeSubscription(3).cancel());

        assertThatThrownBy(() -> subscriptions.save(TestData.activeSubscription(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aStudent_canHaveManyExpiredSubscriptionsAsHistory() throws Exception {
        for (int i = 0; i < 3; i++) {
            Subscription saved = subscriptions.save(TestData.activeSubscription(3));
            subscriptions.save(saved.expire());
        }

        assertThat(queryString("SELECT count(*) FROM subscriptions WHERE status = 'Expired'")).isEqualTo("3");
    }

    @Test
    void dueForExpiration_listsOnlyTheSubscriptionsWhosePeriodEndedAndHaveNotExpired() {
        Subscription ended = subscriptions.save(TestData.withPeriodEnd(TestData.activeSubscription(3),
                Instant.now().minus(Duration.ofHours(1))));
        subscriptions.save(TestData.withPeriodEnd(TestData.activeSubscription(4).cancel(),
                Instant.now().minus(Duration.ofHours(2))));
        subscriptions.save(TestData.activeSubscription(5));
        Subscription expired = subscriptions.save(TestData.withPeriodEnd(TestData.activeSubscription(6),
                Instant.now().minus(Duration.ofDays(1))));
        subscriptions.save(expired.expire());

        assertThat(subscriptions.findDueForExpiration(Instant.now()))
                .extracting(Subscription::getStudentId)
                .containsExactly(4, 3);
        assertThat(ended.getId()).isNotNull();
    }

    @Test
    void theDatabaseRefusesAnUnknownStatusOrANegativePrice() {
        subscriptions.save(TestData.activeSubscription(3));

        assertThatThrownBy(() -> execute("UPDATE subscriptions SET status = 'Paused'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE subscriptions SET plan_price = -1"))
                .isInstanceOf(SQLException.class);
    }

    // ---------- Processed webhook events ----------

    @Test
    void processedEvent_isStoredAndFoundByItsId() {
        processedEvents.save(new ProcessedWebhookEvent("evt-1", "RENEWAL", "3", "PRODUCTION"));

        assertThat(processedEvents.existsByEventId("evt-1")).isTrue();
        assertThat(processedEvents.existsByEventId("evt-2")).isFalse();
    }

    @Test
    void processedEvent_cannotBeRecordedTwice() {
        processedEvents.save(new ProcessedWebhookEvent("evt-1", "RENEWAL", "3", "PRODUCTION"));

        assertThatThrownBy(() -> processedEvents.save(new ProcessedWebhookEvent("evt-1", "RENEWAL", "3", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
