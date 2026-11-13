package com.innovify.skillswap.subscriptionbilling.application.internal.commandservices;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.WebhookEventOutcome;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CancelSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ExpireSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionActivated;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionExpired;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionPlan;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.ProcessedWebhookEventRepository;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Subscription command service.
 *
 * <p>Like the other command services it is not {@code @Transactional}. The gateway is called outside of any
 * transaction (it is a network call); only applying its answer runs in the given {@link TransactionOperations},
 * which reads the current subscription of the student with a lock. A student has at most one subscription that
 * has not expired (a partial unique index): when a webhook and the app create it at the same time, the loser
 * applies the answer again on the row the winner created. Events are published once it is committed.
 */
public class SubscriptionCommandServiceImpl implements SubscriptionCommandService {

    static final String TEST_EVENT = "TEST";
    static final String SANDBOX_ENVIRONMENT = "SANDBOX";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCommandServiceImpl.class);

    private final SubscriptionRepository subscriptions;
    private final ProcessedWebhookEventRepository processedEvents;
    private final PaymentGateway paymentGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final boolean acceptSandboxEvents;
    private final SubscriptionBillingFailures failures;

    /**
     * @param acceptSandboxEvents whether the webhook notifications of test purchases (environment SANDBOX) are
     *                            applied; when false they are acknowledged and ignored
     */
    public SubscriptionCommandServiceImpl(SubscriptionRepository subscriptions,
                                          ProcessedWebhookEventRepository processedEvents,
                                          PaymentGateway paymentGateway,
                                          DomainEventPublisher eventPublisher,
                                          TransactionOperations transactions,
                                          boolean acceptSandboxEvents,
                                          MessageSource messageSource) {
        this.subscriptions = subscriptions;
        this.processedEvents = processedEvents;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.acceptSandboxEvents = acceptSandboxEvents;
        this.failures = new SubscriptionBillingFailures(messageSource);
    }

    @Override
    public Result<Subscription> handle(CreateSubscriptionCommand command) {
        String productId = command.productId() == null ? null : command.productId().strip();
        if (productId != null && (productId.isEmpty() || productId.length() > SubscriptionPlan.MAX_PRODUCT_ID_LENGTH)) {
            return failures.failure(SubscriptionBillingError.INVALID_PRODUCT);
        }

        PurchaseVerification verification;
        try {
            verification = paymentGateway.verifyPurchase(command.studentId(), productId);
        } catch (PaymentGatewayException exception) {
            log.warn("The purchase of student {} could not be verified: {}", command.studentId(),
                    exception.getMessage());
            return failures.failure(SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        if (!verification.active()) {
            return failures.failure(SubscriptionBillingError.PURCHASE_NOT_VERIFIED);
        }

        try {
            Optional<Subscription> applied = applyWithRetry(command.studentId(), verification, null);
            return applied.<Result<Subscription>>map(Result::success)
                    .orElseGet(() -> failures.failure(SubscriptionBillingError.PURCHASE_NOT_VERIFIED));
        } catch (RuntimeException exception) {
            log.error("Could not activate the subscription of student {}", command.studentId(), exception);
            return failures.failure(SubscriptionBillingFailures.toError(exception));
        }
    }

    @Override
    public Result<Subscription> handle(CancelSubscriptionCommand command) {
        try {
            Optional<Subscription> found = subscriptions.findById(command.subscriptionId());
            if (found.isEmpty()) {
                return failures.failure(SubscriptionBillingError.SUBSCRIPTION_NOT_FOUND);
            }
            Subscription subscription = found.get();
            if (!subscription.isOwnedBy(command.studentId())) {
                return failures.failure(SubscriptionBillingError.NOT_SUBSCRIPTION_OWNER);
            }
            if (subscription.isExpired()) {
                return failures.failure(SubscriptionBillingError.SUBSCRIPTION_NOT_ACTIVE);
            }
            if (subscription.isCancelled()) {
                return Result.success(subscription);
            }

            try {
                paymentGateway.cancelRenewal(subscription.getStudentId(), subscription.getStoreTransactionId());
            } catch (PaymentGatewayException exception) {
                log.warn("The renewals of subscription {} could not be cancelled: {}", subscription.getId(),
                        exception.getMessage());
                return failures.failure(SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
            }

            // The webhook of the cancellation may have arrived meanwhile: the row is read again with a lock.
            Subscription cancelled = transactions.execute(status -> {
                Subscription current = subscriptions.findCurrentByStudentIdForUpdate(subscription.getStudentId())
                        .filter(candidate -> candidate.getId().equals(subscription.getId()))
                        .orElse(null);
                if (current == null || current.isCancelled()) {
                    return current;
                }
                return subscriptions.save(current.cancel());
            });
            if (cancelled == null) {
                return failures.failure(SubscriptionBillingError.SUBSCRIPTION_NOT_ACTIVE);
            }
            return Result.success(cancelled);
        } catch (RuntimeException exception) {
            log.error("Could not cancel the subscription {}", command.subscriptionId(), exception);
            return failures.failure(SubscriptionBillingFailures.toError(exception));
        }
    }

    @Override
    public Result<Subscription> handle(ExpireSubscriptionCommand command) {
        try {
            Optional<Subscription> found = subscriptions.findById(command.subscriptionId());
            if (found.isEmpty()) {
                return failures.failure(SubscriptionBillingError.SUBSCRIPTION_NOT_FOUND);
            }
            Subscription subscription = found.get();
            if (subscription.isExpired() || subscription.grantsPremiumAt(Instant.now())) {
                return Result.success(subscription);
            }

            PurchaseVerification verification;
            try {
                verification = paymentGateway.verifyPurchase(subscription.getStudentId(), null);
            } catch (PaymentGatewayException exception) {
                log.warn("The subscription {} could not be checked: {}", subscription.getId(),
                        exception.getMessage());
                return failures.failure(SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
            }

            Optional<Subscription> applied = applyWithRetry(subscription.getStudentId(), verification, null);
            return Result.success(applied.orElse(subscription));
        } catch (RuntimeException exception) {
            log.error("Could not expire the subscription {}", command.subscriptionId(), exception);
            return failures.failure(SubscriptionBillingFailures.toError(exception));
        }
    }

    @Override
    public Result<WebhookEventOutcome> handle(ProcessRevenueCatEventCommand command) {
        if (isBlank(command.eventId()) || isBlank(command.type())
                || command.eventId().strip().length() > ProcessedWebhookEvent.MAX_EVENT_ID_LENGTH) {
            return failures.failure(SubscriptionBillingError.INVALID_WEBHOOK_EVENT);
        }
        // The test events of the dashboard only check that the URL and the Authorization header work.
        if (TEST_EVENT.equalsIgnoreCase(command.type().strip())) {
            return Result.success(WebhookEventOutcome.IGNORED);
        }

        String eventId = command.eventId().strip();
        try {
            if (processedEvents.existsByEventId(eventId)) {
                return Result.success(WebhookEventOutcome.DUPLICATE);
            }

            if (!acceptSandboxEvents && SANDBOX_ENVIRONMENT.equalsIgnoreCase(command.environment())) {
                return record(command, WebhookEventOutcome.IGNORED);
            }

            OptionalInt studentId = resolveStudent(command);
            if (studentId.isEmpty()) {
                log.warn("The webhook event {} ({}) does not belong to a student of the platform", eventId,
                        command.type());
                return record(command, WebhookEventOutcome.IGNORED);
            }

            PurchaseVerification verification;
            try {
                verification = paymentGateway.verifyPurchase(studentId.getAsInt(), null);
            } catch (PaymentGatewayException exception) {
                log.warn("The webhook event {} could not be applied: {}", eventId, exception.getMessage());
                return failures.failure(SubscriptionBillingError.PAYMENT_GATEWAY_UNAVAILABLE);
            }

            applyWithRetry(studentId.getAsInt(), verification, command);
            return Result.success(WebhookEventOutcome.PROCESSED);
        } catch (DuplicateWebhookEventException duplicate) {
            return Result.success(WebhookEventOutcome.DUPLICATE);
        } catch (RuntimeException exception) {
            log.error("Could not process the webhook event {}", eventId, exception);
            return failures.failure(SubscriptionBillingFailures.toError(exception));
        }
    }

    // ---------- Applying the state reported by the gateway ----------

    private Result<WebhookEventOutcome> record(ProcessRevenueCatEventCommand command, WebhookEventOutcome outcome) {
        try {
            transactions.executeWithoutResult(status -> processedEvents.save(toProcessedEvent(command)));
        } catch (DataIntegrityViolationException exception) {
            return Result.success(WebhookEventOutcome.DUPLICATE);
        }
        return Result.success(outcome);
    }

    /**
     * Applies the state, retrying once when another request created the subscription of the student at the same
     * time (the unique index rejects the second one). When the conflict is the event itself, it is a duplicate.
     */
    private Optional<Subscription> applyWithRetry(int studentId, PurchaseVerification verification,
                                                  ProcessRevenueCatEventCommand event) {
        try {
            return apply(studentId, verification, event);
        } catch (DataIntegrityViolationException conflict) {
            if (event != null && processedEvents.existsByEventId(event.eventId().strip())) {
                throw new DuplicateWebhookEventException();
            }
            log.info("The subscription of student {} changed meanwhile; applying the state again", studentId);
            return apply(studentId, verification, event);
        }
    }

    /**
     * Brings the current subscription of the student in line with the gateway, recording the webhook event in the
     * same transaction. Returns the subscription it created, changed or expired.
     */
    private Optional<Subscription> apply(int studentId, PurchaseVerification verification,
                                         ProcessRevenueCatEventCommand event) {
        List<DomainEvent> raised = new ArrayList<>();
        Optional<Subscription> applied = transactions.execute(status -> {
            if (event != null) {
                processedEvents.save(toProcessedEvent(event));
            }

            Optional<Subscription> found = subscriptions.findCurrentByStudentIdForUpdate(studentId);
            if (!verification.active()) {
                if (found.isEmpty()) {
                    return Optional.<Subscription>empty();
                }
                Subscription expired = subscriptions.save(found.get().expire());
                raised.add(new SubscriptionExpired(expired.getId(), studentId, expired.getExpiredAt()));
                return Optional.of(expired);
            }

            if (found.isEmpty()) {
                Subscription created = new Subscription(studentId, SubscriptionPlan.monthly(verification.productId()),
                        verification.storeTransactionId(), verification.expiresAt());
                if (!verification.willRenew()) {
                    created.cancel();
                }
                Subscription saved = subscriptions.save(created);
                raised.add(new SubscriptionActivated(saved.getId(), studentId, saved.getCurrentPeriodEnd()));
                return Optional.of(saved);
            }

            Subscription current = found.get();
            current.renew(verification.expiresAt(), verification.storeTransactionId());
            if (!verification.willRenew() && !current.isCancelled()) {
                current.cancel();
            } else if (verification.willRenew() && current.isCancelled()) {
                current.uncancel();
            }
            return Optional.of(subscriptions.save(current));
        });

        // Published once the transaction is committed.
        raised.forEach(eventPublisher::publish);
        return applied;
    }

    private static ProcessedWebhookEvent toProcessedEvent(ProcessRevenueCatEventCommand command) {
        return new ProcessedWebhookEvent(command.eventId(), command.type(), command.appUserId(),
                command.environment());
    }

    /**
     * The app identifies the customer in RevenueCat with the id of the student, so one of the ids of the event is
     * that number. Anonymous RevenueCat ids ($RCAnonymousID:...) are not students.
     */
    static OptionalInt resolveStudent(ProcessRevenueCatEventCommand command) {
        List<String> candidates = new ArrayList<>();
        candidates.add(command.appUserId());
        candidates.add(command.originalAppUserId());
        candidates.addAll(command.aliases());
        for (String candidate : candidates) {
            if (candidate == null || !candidate.strip().matches("[1-9][0-9]{0,8}")) {
                continue;
            }
            return OptionalInt.of(Integer.parseInt(candidate.strip()));
        }
        return OptionalInt.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The event was recorded by another delivery of the same notification. */
    private static final class DuplicateWebhookEventException extends RuntimeException {

        DuplicateWebhookEventException() {
            super("The webhook event was already processed.", null, false, false);
        }
    }
}
