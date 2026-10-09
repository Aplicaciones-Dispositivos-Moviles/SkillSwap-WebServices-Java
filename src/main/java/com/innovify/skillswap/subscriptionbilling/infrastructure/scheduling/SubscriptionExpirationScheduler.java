package com.innovify.skillswap.subscriptionbilling.infrastructure.scheduling;

import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ExpireSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for the webhooks: a subscription whose paid period ended is checked with the gateway, which renews or
 * expires it. Without it a lost notification (or the simulated gateway, which sends none) would never send the
 * student back to the free plan. The plan limits do not wait for it: a period that ended already counts as free.
 */
@Component
@ConditionalOnProperty(name = "billing.expiration-check-enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationScheduler.class);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionCommandService commandService;

    public SubscriptionExpirationScheduler(SubscriptionRepository subscriptions,
                                           SubscriptionCommandService commandService) {
        this.subscriptions = subscriptions;
        this.commandService = commandService;
    }

    @Scheduled(initialDelayString = "${billing.expiration-check-interval:1h}",
            fixedDelayString = "${billing.expiration-check-interval:1h}")
    public void checkEndedPeriods() {
        int failed = 0;
        for (Subscription due : subscriptions.findDueForExpiration(Instant.now())) {
            if (commandService.handle(new ExpireSubscriptionCommand(due.getId())).isFailure()) {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("{} subscriptions with an ended period could not be checked; they are retried later", failed);
        }
    }
}
