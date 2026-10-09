package com.innovify.skillswap.learningpathengine.application.eventhandlers;

import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.commands.EnforcePlanLimitsCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionExpired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The subscription of a student expired and they are back on the free plan: the path they advanced on most
 * recently stays active and the other active ones are paused. Nothing is deleted, and the student can choose
 * another one later by pausing the active path. The event arrives after the commit of the subscription, so the
 * paths change in a transaction of their own.
 */
public class EnforcePlanLimitsEventHandler implements DomainEventHandler<SubscriptionExpired> {

    private static final Logger log = LoggerFactory.getLogger(EnforcePlanLimitsEventHandler.class);

    private final LearningPathCommandService commandService;
    private final TransactionOperations newTransaction;

    /** @param newTransaction must start a new transaction (REQUIRES_NEW) */
    public EnforcePlanLimitsEventHandler(LearningPathCommandService commandService,
                                         TransactionOperations newTransaction) {
        this.commandService = commandService;
        this.newTransaction = newTransaction;
    }

    @Override
    public void handle(SubscriptionExpired event) {
        var result = newTransaction.execute(status -> commandService.handle(
                new EnforcePlanLimitsCommand(event.studentId())));
        if (result == null || result.isFailure()) {
            log.warn("The paths of student {} were not adapted to the free plan: {}", event.studentId(),
                    result == null ? null : result.error());
        } else if (!result.value().isEmpty()) {
            log.info("{} paths of student {} were paused after the subscription expired", result.value().size(),
                    event.studentId());
        }
    }
}
